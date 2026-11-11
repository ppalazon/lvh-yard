package cc.lvhy.yard.dashboard.consul;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiPredicate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Consul in memory, for component tests: this Workstation's datacenter and those of other
 * Workstations. No check runs: a test sets each check's status. ConsulContract keeps it in step
 * with {@link ConsulAgent}.
 */
public final class FakeConsul implements Consul {

  /** This Workstation's LAN address, as its agent gives it. */
  public static final String LAN_ADDRESS = "10.20.0.1";

  private final String home;

  /** The federation.datacenters and what listens, which every Workstation's FakeConsul shares. */
  private final Federation federation;

  public FakeConsul(String home) {
    this(home, new Federation());
    federation.datacenters.put(home, new Datacenter(LAN_ADDRESS));
  }

  private FakeConsul(String home, Federation federation) {
    this.home = home;
    this.federation = federation;
  }

  /**
   * The Consul of Workstation {@code other}, as its own dashboard reaches it, federated with this
   * one: both see the same federation.datacenters.
   */
  public FakeConsul as(String other) {
    datacenter(other);
    return new FakeConsul(other, federation);
  }

  /** Another Workstation's datacenter, federated with this one. It is made at the first call. */
  public Datacenter datacenter(String name) {
    synchronized (federation) {
      return federation.datacenters.computeIfAbsent(
          name, created -> new Datacenter("10.20." + federation.datacenters.size() + ".1"));
    }
  }

  /** Sets the status of the check of registration {@code id}, such as passing, with its output. */
  public void setStatus(String id, String status, String output) {
    synchronized (federation) {
      for (Datacenter datacenter : federation.datacenters.values()) {
        Stored stored = datacenter.services.get(id);
        if (stored != null) {
          datacenter.services.put(id, new Stored(stored.service(), stored.check(), status, output));
          changed();
          return;
        }
      }
      throw new IllegalArgumentException("No registration " + id);
    }
  }

  /** Something accepts TCP connections at {@code host} and {@code port} from now on. */
  public void listen(String host, int port) {
    synchronized (federation) {
      federation.listening.put(host + ":" + port, new Answer(200, "200 OK", ""));
      changed();
    }
  }

  /**
   * HTTP requests to {@code host} and {@code port} get {@code status}, such as 503, and {@code
   * body}.
   */
  public void answerHttp(String host, int port, String statusLine, String body) {
    synchronized (federation) {
      int code = Integer.parseInt(statusLine.substring(0, 3));
      federation.listening.put(host + ":" + port, new Answer(code, statusLine, body));
      changed();
    }
  }

  /** {@code more} also says what listens, after what {@link #listen} was told. */
  public void alsoListening(BiPredicate<String, Integer> more) {
    synchronized (federation) {
      federation.alsoListening = more;
    }
  }

  public void stopListening(String host, int port) {
    synchronized (federation) {
      federation.listening.remove(host + ":" + port);
      changed();
    }
  }

  /** Consul's ModifyIndex of {@code key} in this Workstation's datacenter: its last write. */
  public long modifyIndex(String key) {
    synchronized (federation) {
      return own().modified.getOrDefault(key, 0L);
    }
  }

  /** Removes the check of registration {@code id} and keeps the registration. */
  public void removeCheck(String id) {
    synchronized (federation) {
      Stored stored = own().services.get(id);
      own().services.put(id, new Stored(stored.service(), null, null, ""));
      changed();
    }
  }

  /** What every datacenter holds now, for {@link #restore}. */
  public Map<String, Datacenter> snapshot() {
    synchronized (federation) {
      Map<String, Datacenter> copy = new LinkedHashMap<>();
      federation.datacenters.forEach((name, datacenter) -> copy.put(name, datacenter.copy()));
      return copy;
    }
  }

  /** Back to {@code snapshot}, with every datacenter answering. */
  public void restore(Map<String, Datacenter> snapshot) {
    synchronized (federation) {
      federation.datacenters.clear();
      snapshot.forEach((name, datacenter) -> federation.datacenters.put(name, datacenter.copy()));
      federation.listening.clear();
      changed();
    }
  }

  @Override
  public List<String> datacenters() {
    synchronized (federation) {
      return List.copyOf(federation.datacenters.keySet());
    }
  }

  @Override
  public String leader() {
    synchronized (federation) {
      return own().leader ? own().address + ":8300" : "";
    }
  }

  @Override
  public List<WanMember> wanMembers() {
    synchronized (federation) {
      List<WanMember> members = new ArrayList<>();
      federation.datacenters.forEach((name, datacenter) ->
          members.add(
              new WanMember(
                  datacenter.address,
                  8302,
                  datacenter.answering ? SerfStatus.ALIVE : SerfStatus.FAILED,
                  Map.of("dc", name))));
      return members;
    }
  }

  @Override
  public String agentAddress() {
    synchronized (federation) {
      return own().address;
    }
  }

  @Override
  public void register(Registration registration) {
    synchronized (federation) {
      own().register(registration);
      changed();
    }
  }

  @Override
  public Map<String, AgentService> agentServices() {
    synchronized (federation) {
      Map<String, AgentService> services = new LinkedHashMap<>();
      own().services.forEach((id, stored) -> services.put(id, stored.service()));
      return services;
    }
  }

  @Override
  public Optional<AgentService> agentService(String id) {
    synchronized (federation) {
      return Optional.ofNullable(own().services.get(id)).map(Stored::service);
    }
  }

  @Override
  public void deregister(String id) {
    synchronized (federation) {
      own().services.remove(id);
      changed();
    }
  }

  @Override
  public List<String> serviceNames(String datacenter) {
    synchronized (federation) {
      List<String> names = new ArrayList<>();
      names.add(CONSUL_SERVICE);
      in(datacenter).services.values().stream()
          .map(stored -> stored.service().name())
          .distinct()
          .sorted()
          .forEach(names::add);
      return names;
    }
  }

  @Override
  public List<HealthEntry> health(String datacenter, String service) {
    synchronized (federation) {
      Datacenter in = in(datacenter);
      CheckStatus node =
          new CheckStatus(
              "Serf Health Status", "passing", "Agent alive and reachable", "", "", "", "");
      List<HealthEntry> entries = new ArrayList<>();
      if (service.equals(CONSUL_SERVICE)) {
        entries.add(
            new HealthEntry(
                new AgentService(CONSUL_SERVICE, CONSUL_SERVICE, in.address, 8300, null, null),
                List.of(node)));
      }
      in.services.values().stream()
          .filter(stored -> stored.service().name().equals(service))
          .forEach(stored -> {
            List<CheckStatus> checks = new ArrayList<>(List.of(node));
            if (stored.check() != null) {
              Check check = stored.check();
              checks.add(
                  new CheckStatus(
                      check.name() == null ? "Service '" + service + "' check" : check.name(),
                      stored.status() != null ? stored.status() : derivedStatus(check),
                      stored.status() != null ? stored.output() : derivedOutput(check),
                      stored.service().id(),
                      check.http() != null ? "http" : "tcp",
                      check.interval() == null ? "" : check.interval(),
                      check.timeout() == null ? "" : check.timeout()));
            }
            entries.add(new HealthEntry(stored.service(), checks));
          });
      return entries;
    }
  }

  @Override
  public void putValue(String key, String value) {
    synchronized (federation) {
      own().values.put(key, value);
      changed();
      own().modified.put(key, federation.index);
    }
  }

  @Override
  public void putValues(Map<String, String> values) {
    synchronized (federation) {
      if (values.size() > TRANSACTION_SIZE) {
        throw new IllegalArgumentException(
            "One Consul transaction takes at most " + TRANSACTION_SIZE + " writes.");
      }
      if (values.isEmpty()) {
        return;
      }
      own().values.putAll(values);
      changed();
      values.keySet().forEach(key -> own().modified.put(key, federation.index));
    }
  }

  @Override
  public Map<String, String> values(String datacenter, String prefix) {
    synchronized (federation) {
      Map<String, String> found = new TreeMap<>();
      in(datacenter)
          .values
          .forEach((key, value) -> {
            if (key.startsWith(prefix)) {
              found.put(key, value);
            }
          });
      return found;
    }
  }

  @Override
  public void deleteValue(String key) {
    synchronized (federation) {
      if (own().values.remove(key) != null) {
        changed();
      }
    }
  }

  @Override
  public long awaitChange(Watched watched, long index, Duration wait) {
    synchronized (federation) {
      if (index > 0 && federation.index <= index) {
        try {
          federation.wait(wait.toMillis());
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Stopped while waiting for Consul", e);
        }
      }
      return federation.index;
    }
  }

  private Datacenter own() {
    return federation.datacenters.get(home);
  }

  /** As the local agent forwards a read: an unknown datacenter fails, and so does a silent one. */
  private Datacenter in(String name) {
    Datacenter datacenter = federation.datacenters.get(name == null ? home : name);
    if (datacenter == null) {
      throw HttpServerErrorException.create(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Internal Server Error",
          HttpHeaders.EMPTY,
          "No path to datacenter".getBytes(StandardCharsets.UTF_8),
          StandardCharsets.UTF_8);
    }
    if (!datacenter.answering) {
      throw new ResourceAccessException("I/O error on GET request: request timed out");
    }
    return datacenter;
  }

  private void changed() {
    federation.index++;
    federation.notifyAll();
  }

  /** As Consul runs the check against what listens: 2xx passes, 429 warns, else it fails. */
  private String derivedStatus(Check check) {
    Answer answer = answerAt(target(check));
    if (answer == null) {
      return "critical";
    }
    if (check.http() == null || answer.code() / 100 == 2) {
      return "passing";
    }
    return answer.code() == 429 ? "warning" : "critical";
  }

  /** In the words Consul uses for each outcome. */
  private String derivedOutput(Check check) {
    String target = target(check);
    Answer answer = answerAt(target);
    if (check.http() == null) {
      return answer == null
          ? "dial tcp " + target + ": connect: connection refused"
          : "TCP connect " + target + ": Success";
    }
    return answer == null
        ? "Get \"" + check.http() + "\": dial tcp " + target + ": connect: connection refused"
        : "HTTP GET " + check.http() + ": " + answer.statusLine() + " Output: " + answer.body();
  }

  private Answer answerAt(String target) {
    Answer answer = federation.listening.get(target);
    if (answer != null) {
      return answer;
    }
    int colon = target.lastIndexOf(':');
    return federation.alsoListening.test(
            target.substring(0, colon), Integer.parseInt(target.substring(colon + 1)))
        ? new Answer(200, "200 OK", "")
        : null;
  }

  /** The {@code host:port} that the check connects to. */
  private static String target(Check check) {
    if (check.tcp() != null) {
      return check.tcp();
    }
    String rest = check.http().substring(check.http().indexOf("://") + 3);
    int slash = rest.indexOf('/');
    return slash < 0 ? rest : rest.substring(0, slash);
  }

  private record Answer(int code, String statusLine, String body) {}

  /** The state that the FakeConsul of each Workstation shares, and the lock that guards it. */
  private static final class Federation {

    private final Map<String, Datacenter> datacenters = new LinkedHashMap<>();

    /**
     * What answers at each {@code host:port}: an HTTP status line and body. A check whose status no
     * test set passes or fails from this.
     */
    private final Map<String, Answer> listening = new HashMap<>();

    /** What else listens, such as the containers of FakeDocker. */
    private BiPredicate<String, Integer> alsoListening = (host, port) -> false;

    /** One index for every change, as a blocking query only needs it to grow. */
    private long index = 1;
  }

  /** {@code status} is null until a test sets it: the check then runs against what listens. */
  private record Stored(AgentService service, Check check, String status, String output) {}

  /** One Workstation's datacenter. A test changes another Workstation's through it. */
  public final class Datacenter {

    private final String address;

    private final Map<String, Stored> services = new LinkedHashMap<>();

    private final Map<String, String> values = new TreeMap<>();

    /** The index of the last write to each key, as Consul's ModifyIndex. */
    private final Map<String, Long> modified = new HashMap<>();

    private boolean answering = true;

    private boolean leader = true;

    private Datacenter(String address) {
      this.address = address;
    }

    /** As that Workstation's dashboard registers it. Its check, if any, starts critical. */
    public void register(Registration registration) {
      synchronized (federation) {
        services.put(
            registration.id(),
            new Stored(
                new AgentService(
                    registration.id(),
                    registration.name(),
                    registration.address(),
                    registration.port(),
                    registration.tags(),
                    registration.meta()),
                registration.check(),
                null,
                ""));
        changed();
      }
    }

    public void putValue(String key, String value) {
      synchronized (federation) {
        values.put(key, value);
        changed();
        modified.put(key, federation.index);
      }
    }

    /** As a switched-off computer: reads fail and its WAN member is failed. */
    public void stopAnswering() {
      synchronized (federation) {
        answering = false;
        changed();
      }
    }

    public void answerAgain() {
      synchronized (federation) {
        answering = true;
        changed();
      }
    }

    /** Whether it has elected a leader. Only this Workstation's own datacenter is asked. */
    public void hasLeader(boolean elected) {
      synchronized (federation) {
        leader = elected;
        changed();
      }
    }

    private Datacenter copy() {
      Datacenter copy = new Datacenter(address);
      copy.services.putAll(services);
      copy.values.putAll(values);
      copy.modified.putAll(modified);
      return copy;
    }
  }
}
