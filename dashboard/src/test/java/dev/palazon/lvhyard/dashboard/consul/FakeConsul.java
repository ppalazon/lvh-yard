package dev.palazon.lvhyard.dashboard.consul;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
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

  private final Map<String, Datacenter> datacenters = new LinkedHashMap<>();

  /** One index for every change, as a blocking query only needs it to grow. */
  private long index = 1;

  public FakeConsul(String home) {
    this.home = home;
    datacenters.put(home, new Datacenter(LAN_ADDRESS));
  }

  /** Another Workstation's datacenter, federated with this one. It is made at the first call. */
  public synchronized Datacenter datacenter(String name) {
    return datacenters.computeIfAbsent(
        name, created -> new Datacenter("10.20." + datacenters.size() + ".1"));
  }

  /** Sets the status of the check of registration {@code id}, such as passing, with its output. */
  public synchronized void setStatus(String id, String status, String output) {
    for (Datacenter datacenter : datacenters.values()) {
      Stored stored = datacenter.services.get(id);
      if (stored != null) {
        datacenter.services.put(id, new Stored(stored.service(), stored.check(), status, output));
        changed();
        return;
      }
    }
    throw new IllegalArgumentException("No registration " + id);
  }

  /** What every datacenter holds now, for {@link #restore}. */
  public synchronized Map<String, Datacenter> snapshot() {
    Map<String, Datacenter> copy = new LinkedHashMap<>();
    datacenters.forEach((name, datacenter) -> copy.put(name, datacenter.copy()));
    return copy;
  }

  /** Back to {@code snapshot}, with every datacenter answering. */
  public synchronized void restore(Map<String, Datacenter> snapshot) {
    datacenters.clear();
    snapshot.forEach((name, datacenter) -> datacenters.put(name, datacenter.copy()));
    changed();
  }

  @Override
  public synchronized List<String> datacenters() {
    return List.copyOf(datacenters.keySet());
  }

  @Override
  public synchronized String leader() {
    return own().leader ? own().address + ":8300" : "";
  }

  @Override
  public synchronized List<WanMember> wanMembers() {
    List<WanMember> members = new ArrayList<>();
    datacenters.forEach((name, datacenter) ->
        members.add(
            new WanMember(
                datacenter.address,
                8302,
                datacenter.answering ? SerfStatus.ALIVE : SerfStatus.FAILED,
                Map.of("dc", name))));
    return members;
  }

  @Override
  public String agentAddress() {
    return LAN_ADDRESS;
  }

  @Override
  public synchronized void register(Registration registration) {
    own().register(registration);
    changed();
  }

  @Override
  public synchronized Map<String, AgentService> agentServices() {
    Map<String, AgentService> services = new LinkedHashMap<>();
    own().services.forEach((id, stored) -> services.put(id, stored.service()));
    return services;
  }

  @Override
  public synchronized Optional<AgentService> agentService(String id) {
    return Optional.ofNullable(own().services.get(id)).map(Stored::service);
  }

  @Override
  public synchronized void deregister(String id) {
    own().services.remove(id);
    changed();
  }

  @Override
  public synchronized List<String> serviceNames(String datacenter) {
    List<String> names = new ArrayList<>();
    names.add(CONSUL_SERVICE);
    in(datacenter).services.values().stream()
        .map(stored -> stored.service().name())
        .distinct()
        .sorted()
        .forEach(names::add);
    return names;
  }

  @Override
  public synchronized List<HealthEntry> health(String datacenter, String service) {
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
                    stored.status(),
                    stored.output(),
                    stored.service().id(),
                    check.http() != null ? "http" : "tcp",
                    check.interval() == null ? "" : check.interval(),
                    check.timeout() == null ? "" : check.timeout()));
          }
          entries.add(new HealthEntry(stored.service(), checks));
        });
    return entries;
  }

  @Override
  public synchronized void putValue(String key, String value) {
    own().values.put(key, value);
    changed();
  }

  @Override
  public synchronized void putValues(Map<String, String> values) {
    if (values.size() > TRANSACTION_SIZE) {
      throw new IllegalArgumentException(
          "One Consul transaction takes at most " + TRANSACTION_SIZE + " writes.");
    }
    if (values.isEmpty()) {
      return;
    }
    own().values.putAll(values);
    changed();
  }

  @Override
  public synchronized Map<String, String> values(String datacenter, String prefix) {
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

  @Override
  public synchronized void deleteValue(String key) {
    if (own().values.remove(key) != null) {
      changed();
    }
  }

  @Override
  public synchronized long awaitChange(Watched watched, long index, Duration wait) {
    if (index > 0 && this.index <= index) {
      try {
        wait(wait.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Stopped while waiting for Consul", e);
      }
    }
    return this.index;
  }

  private Datacenter own() {
    return datacenters.get(home);
  }

  /** As the local agent forwards a read: an unknown datacenter fails, and so does a silent one. */
  private Datacenter in(String name) {
    Datacenter datacenter = datacenters.get(name == null ? home : name);
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
    index++;
    notifyAll();
  }

  private record Stored(AgentService service, Check check, String status, String output) {}

  /** One Workstation's datacenter. A test changes another Workstation's through it. */
  public final class Datacenter {

    private final String address;

    private final Map<String, Stored> services = new LinkedHashMap<>();

    private final Map<String, String> values = new TreeMap<>();

    private boolean answering = true;

    private boolean leader = true;

    private Datacenter(String address) {
      this.address = address;
    }

    /** As that Workstation's dashboard registers it. Its check, if any, starts critical. */
    public void register(Registration registration) {
      synchronized (FakeConsul.this) {
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
                registration.check() == null ? null : "critical",
                ""));
        changed();
      }
    }

    public void putValue(String key, String value) {
      synchronized (FakeConsul.this) {
        values.put(key, value);
        changed();
      }
    }

    /** As a switched-off computer: reads fail and its WAN member is failed. */
    public void stopAnswering() {
      synchronized (FakeConsul.this) {
        answering = false;
        changed();
      }
    }

    public void answerAgain() {
      synchronized (FakeConsul.this) {
        answering = true;
        changed();
      }
    }

    /** Whether it has elected a leader. Only this Workstation's own datacenter is asked. */
    public void hasLeader(boolean elected) {
      synchronized (FakeConsul.this) {
        leader = elected;
        changed();
      }
    }

    private Datacenter copy() {
      Datacenter copy = new Datacenter(address);
      copy.services.putAll(services);
      copy.values.putAll(values);
      return copy;
    }
  }
}
