package dev.palazon.lvhyard.dashboard.docker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Docker in memory, for component tests. A test starts, stops and removes containers as plain data,
 * and each change sends the event that Docker sends. DockerContract keeps it in step with {@link
 * DockerEngine}.
 */
public final class FakeDocker implements Docker {

  /** The network that every container joins, unless it is on the host's network. */
  public static final String NETWORK = "yard-test";

  private final Map<String, Container> running = new LinkedHashMap<>();

  private final List<Recorded> events = new ArrayList<>();

  private final Set<FakeEvents> following = ConcurrentHashMap.newKeySet();

  private volatile String failure;

  private int next;

  /** A container called {@code name}, to describe and then give to {@link #start}. */
  public static Spec container(String name) {
    return new Spec(name);
  }

  /** Starts a container as {@code spec} describes it, and answers it as Docker lists it. */
  public synchronized Container start(Spec spec) {
    next++;
    List<Port> ports = new ArrayList<>();
    for (int port : spec.ports) {
      // Docker sends an empty IP, not none, for a port that is not published.
      ports.add(new Port("", port, null, "tcp"));
    }
    for (Map.Entry<Integer, String> published : spec.published.entrySet()) {
      int hostPort = 40000 + next * 10 + ports.size();
      for (String ip : published.getValue().split(",")) {
        ports.add(new Port(ip, published.getKey(), hostPort, "tcp"));
      }
    }
    Map<String, Network> networks =
        spec.hostNetwork
            ? Map.of("host", new Network(""))
            : Map.of(NETWORK, new Network("172.30." + (next / 250) + "." + (next % 250 + 2)));
    Container container =
        new Container(
            "fake-" + next,
            List.of("/" + spec.name),
            ports,
            new NetworkSettings(networks),
            spec.labels,
            "Up 1 second" + (spec.health == null ? "" : " (" + spec.health + ")"),
            spec.health == null ? null : new Health(spec.health));
    running.put(spec.name, container);
    record("start");
    return container;
  }

  /** Stops the container called {@code name}, as {@code docker stop} does. */
  public synchronized void stop(String name) {
    if (running.remove(name) == null) {
      throw new NoSuchElementException("No running container " + name);
    }
    record("die");
  }

  /** Stops and removes the container called {@code name}, as {@code docker rm -f} does. */
  public synchronized void remove(String name) {
    if (running.remove(name) != null) {
      record("die");
    }
    record("destroy");
  }

  /** Every call fails with {@code reason} from now on, as a Docker that does not answer. */
  public void failWith(String reason) {
    failure = reason;
  }

  /** Back to no containers and no events, and answering. */
  public synchronized void reset() {
    running.clear();
    events.clear();
    failure = null;
  }

  @Override
  public void ping() {
    answer();
  }

  @Override
  public Version version() {
    answer();
    return new Version("28.0.0", "1.48");
  }

  @Override
  public synchronized List<Container> runningContainers() {
    answer();
    return List.copyOf(running.values());
  }

  @Override
  public synchronized ContainerEvents containerEvents(Instant since) {
    answer();
    FakeEvents events = new FakeEvents();
    this.events.stream()
        .filter(recorded -> !recorded.at().isBefore(since))
        .forEach(recorded -> events.queue.add(recorded.event()));
    following.add(events);
    return events;
  }

  private void answer() {
    if (failure != null) {
      throw new IllegalStateException(failure);
    }
  }

  private void record(String action) {
    Event event = new Event("container", action);
    events.add(new Recorded(Instant.now(), event));
    following.forEach(events -> events.queue.add(event));
  }

  private record Recorded(Instant at, Event event) {}

  /** Waits for the next event as Docker's stream does, until closed. */
  private final class FakeEvents implements ContainerEvents {

    /** Stands for the end of the stream. */
    private static final Event END = new Event("", "");

    private final LinkedBlockingQueue<Event> queue = new LinkedBlockingQueue<>();

    private Event peeked;

    @Override
    public boolean hasNext() {
      if (peeked == null) {
        try {
          while (peeked == null) {
            peeked = queue.poll(1, TimeUnit.SECONDS);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return false;
        }
      }
      return peeked != END;
    }

    @Override
    public Event next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      Event event = peeked;
      peeked = null;
      return event;
    }

    @Override
    public void close() {
      following.remove(this);
      queue.add(END);
    }
  }

  /** What a test says about a container before it starts. */
  public static final class Spec {

    private final String name;

    private final Map<String, String> labels = new LinkedHashMap<>();

    private final List<Integer> ports = new ArrayList<>();

    /** Each container port with its host addresses, comma-separated. */
    private final Map<Integer, String> published = new LinkedHashMap<>();

    private boolean hostNetwork;

    private String health;

    private Spec(String name) {
      this.name = name;
    }

    public String name() {
      return name;
    }

    /** The labels that Compose gives a container of {@code service} in {@code project}. */
    public Spec compose(String project, String service) {
      labels.put("com.docker.compose.project", project);
      labels.put("com.docker.compose.service", service);
      return this;
    }

    public Spec label(String key, String value) {
      labels.put(key, value);
      return this;
    }

    /** A port that the image exposes but nothing publishes. */
    public Spec port(int containerPort) {
      ports.add(containerPort);
      return this;
    }

    /** Published on every host address, as Docker does on 0.0.0.0 and ::. */
    public Spec published(int containerPort) {
      published.put(containerPort, "0.0.0.0,::");
      return this;
    }

    /** Published on {@code hostIp} only, such as 127.0.0.1. */
    public Spec publishedOn(String hostIp, int containerPort) {
      published.put(containerPort, hostIp);
      return this;
    }

    public Spec hostNetwork() {
      hostNetwork = true;
      return this;
    }

    /** Its HEALTHCHECK state: starting, healthy or unhealthy. */
    public Spec health(String state) {
      health = state;
      return this;
    }

    List<Integer> ports() {
      return ports;
    }

    Map<Integer, String> publishedPorts() {
      return published;
    }

    Map<String, String> labels() {
      return labels;
    }

    boolean onHostNetwork() {
      return hostNetwork;
    }

    String healthState() {
      return health;
    }
  }
}
