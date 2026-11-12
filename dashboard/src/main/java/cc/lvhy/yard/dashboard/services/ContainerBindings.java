package cc.lvhy.yard.dashboard.services;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.consul.Consul;
import cc.lvhy.yard.dashboard.docker.Docker;
import cc.lvhy.yard.dashboard.docker.Docker.Container;
import cc.lvhy.yard.dashboard.docker.Docker.Port;
import cc.lvhy.yard.dashboard.services.Registrations.BoundInstance;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * One Consul KV key per Binding, under its Service, holding the request as JSON. The {@link
 * ContainerWatcher} says when the containers may have changed.
 */
@Component
public class ContainerBindings {

  private static final Logger LOG = LoggerFactory.getLogger(ContainerBindings.class);

  static final String PREFIX = "lvh-yard/container-bindings/";

  /** A container name, as Docker allows them. */
  private static final Pattern CONTAINER_NAME = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9_.-]{1,254}");

  /** A Compose project name, as Compose allows them. */
  private static final Pattern COMPOSE_PROJECT = Pattern.compile("[a-z0-9][a-z0-9_-]*");

  /** A Compose service name, as Compose allows them. */
  private static final Pattern COMPOSE_SERVICE = Pattern.compile("[a-zA-Z0-9._-]+");

  /**
   * Host addresses that stand for every address of the host, so the LAN address reaches a port
   * published there.
   */
  private static final List<String> EVERY_ADDRESS = List.of("", "0.0.0.0", "::");

  /** Where a Bound Instance is for a loopback target: the Workstation itself. */
  private static final String LOOPBACK = "127.0.0.1";

  private static final ObjectMapper JSON = JsonMapper.builder().build();

  private final Workstation workstation;

  private final Consul consul;

  private final Docker docker;

  private final Registrations registrations;

  private final ServiceRecords records;

  private final Publications publications;

  private final OutOfRange outOfRange;

  ContainerBindings(
      Workstation workstation,
      Consul consul,
      Docker docker,
      Registrations registrations,
      ServiceRecords records,
      Publications publications,
      OutOfRange outOfRange) {
    this.outOfRange = outOfRange;
    this.publications = publications;
    this.records = records;
    this.workstation = workstation;
    this.consul = consul;
    this.docker = docker;
    this.registrations = registrations;
  }

  /**
   * {@code workstation} must be this one. Running containers that match give their Bound Instances
   * at once. Any number of Services can follow one container or Compose service. Adding a Binding
   * of this Service that exists, with the same target and port, changes nothing.
   *
   * @return the Binding's ID
   * @throws UnknownServiceException when this Workstation has no Service of this name
   */
  public String add(String workstation, String service, ContainerBindingRequest request) {
    checkHome(workstation);
    Registrations.checkName(service);
    ContainerBindingRequest binding = checked(request);
    records.ensure(service);
    String id = ContainerBinding.id(binding);
    consul.putValue(PREFIX + service + "/" + id, JSON.writeValueAsString(binding));
    records.addSource(service, id);
    syncIfDockerAnswers();
    return id;
  }

  /**
   * The Binding as {@link #add} would store it. It writes nothing.
   *
   * @throws InvalidRegistrationException at the first rule that {@code request} breaks
   */
  public static ContainerBindingRequest check(String service, ContainerBindingRequest request) {
    Registrations.checkName(service);
    return checked(request);
  }

  /** Strips the names, and fails unless it names a container, or a Compose project and service. */
  private static ContainerBindingRequest checked(ContainerBindingRequest request) {
    String container = stripped(request.container());
    String project = stripped(request.composeProject());
    String composeService = stripped(request.composeService());
    if (project == null && composeService == null) {
      if (container == null) {
        throw new InvalidRegistrationException(
            "container",
            "A Container Binding needs a container name, or" + " a Compose project and service.");
      }
      if (!CONTAINER_NAME.matcher(container).matches()) {
        throw new InvalidRegistrationException(
            "container",
            "The container name \""
                + container
                + "\" is not one Docker allows: start with a letter or"
                + " digit, then use at least one more letter, digit, _, ."
                + " or -.");
      }
      Registrations.checkPort("containerPort", "A Container Binding", request.port());
      return ContainerBindingRequest.container(container, request.port());
    }
    if (container != null) {
      throw new InvalidRegistrationException(
          "container",
          "A Container Binding names a container or a Compose" + " project and service, not both.");
    }
    if (project == null) {
      throw new InvalidRegistrationException(
          "composeProject", "A Compose service needs its project too.");
    }
    if (!COMPOSE_PROJECT.matcher(project).matches()) {
      throw new InvalidRegistrationException(
          "composeProject",
          "The Compose project name \""
              + project
              + "\" is not one Compose allows: start with a lowercase letter or digit, then use"
              + " lowercase letters, digits, _ or -.");
    }
    if (composeService == null) {
      throw new InvalidRegistrationException(
          "composeService", "A Compose project needs the service too.");
    }
    if (!COMPOSE_SERVICE.matcher(composeService).matches()) {
      throw new InvalidRegistrationException(
          "composeService",
          "The Compose service name \""
              + composeService
              + "\" is not one Compose allows: use letters, digits, _, . or -.");
    }
    Registrations.checkPort("containerPort", "A Container Binding", request.port());
    return ContainerBindingRequest.compose(project, composeService, request.port());
  }

  static boolean sameTarget(ContainerBindingRequest one, ContainerBindingRequest other) {
    return one.followsCompose()
        ? one.composeProject().equals(other.composeProject())
            && one.composeService().equals(other.composeService())
        : one.container().equals(other.container());
  }

  /** The Services that have a Binding in {@code followed} that {@code test} accepts, by name. */
  static List<String> followers(
      Map<String, Map<String, ContainerBindingRequest>> followed,
      Predicate<ContainerBindingRequest> test) {
    return followed.entrySet().stream()
        .filter(byService -> byService.getValue().values().stream().anyMatch(test))
        .map(Map.Entry::getKey)
        .sorted()
        .toList();
  }

  private static String stripped(String name) {
    return name == null || name.isBlank() ? null : name.strip();
  }

  /**
   * {@code workstation} must be this one. Removes the Bound Instances too, whether Docker answers
   * or not.
   */
  public void remove(String workstation, String service, String id) {
    checkHome(workstation);
    String key = PREFIX + service + "/" + id;
    if (!consul.values(null, PREFIX + service + "/").containsKey(key)) {
      throw new UnknownContainerBindingException(service, id);
    }
    consul.deleteValue(key);
    registrations.removeBoundInstances(service, id);
    records.removeSource(service, id);
  }

  /** Removes every Binding of {@code service} on this Workstation, with its Bound Instances. */
  void removeAll(String service) {
    all(null)
        .getOrDefault(service, Map.of())
        .keySet()
        .forEach(id -> remove(workstation.name(), service, id));
  }

  /** {@code null} reads this Workstation's own. Each Service's Bindings come by ID. */
  Map<String, Map<String, ContainerBindingRequest>> all(String datacenter) {
    Map<String, Map<String, ContainerBindingRequest>> bindings = new TreeMap<>();
    consul
        .values(datacenter, PREFIX)
        .forEach((key, value) -> {
          String[] serviceAndId = key.substring(PREFIX.length()).split("/", 2);
          if (serviceAndId.length < 2) {
            return;
          }
          try {
            bindings
                .computeIfAbsent(serviceAndId[0], service -> new TreeMap<>())
                .put(serviceAndId[1], JSON.readValue(value, ContainerBindingRequest.class));
          } catch (JacksonException e) {
            throw new IllegalStateException(
                "The Container Binding at " + key + " cannot be read: " + value, e);
          }
        });
    return bindings;
  }

  /**
   * Every Bound Instance that no running container gives is removed.
   *
   * @throws RuntimeException when Docker or Consul does not answer, which leaves the Bound
   *     Instances as they were
   */
  public synchronized void sync() {
    List<Container> running = docker.runningContainers();
    String lanAddress = registrations.defaultHost();
    Map<String, Publication> published = publications.all();
    List<BoundInstance> wanted = new ArrayList<>();
    all(null)
        .forEach((service, bindings) ->
            bindings.forEach((id, binding) ->
                running.stream()
                    .filter(container -> matches(binding, container))
                    .forEach(container ->
                        wanted.add(
                            boundInstance(
                                service,
                                id,
                                binding,
                                container,
                                lanAddress,
                                published.containsKey(service)
                                    ? published.get(service).scope()
                                    : null)))));
    registrations.keepBoundInstances(wanted);
  }

  static boolean matches(ContainerBindingRequest binding, Container container) {
    return binding.followsCompose()
        ? container.isOfComposeService(binding.composeProject(), binding.composeService())
        : container.isNamed(binding.container());
  }

  /** The Bound Instances follow a Publication that was added, changed or removed. */
  @EventListener
  void onPublicationsChanged(PublicationsChanged changed) {
    syncIfDockerAnswers();
  }

  /** A Docker that does not answer leaves the sync to the watcher. */
  private void syncIfDockerAnswers() {
    try {
      sync();
    } catch (RuntimeException e) {
      LOG.warn("The Bound Instances cannot be brought in step with Docker now: {}", e.getMessage());
    }
  }

  /**
   * Where {@code container} gives {@code service} its Bound Instance. A container port published on
   * one address only is at that address and its host port. One published on every host address is
   * at the host port and, for a Local Publication, loopback, else the LAN address, so other
   * Workstations reach it. Otherwise it is at the container's own address and the container port,
   * or, for a container on the host's network, at loopback for a Local Publication, else the LAN
   * address.
   *
   * <p>With several mappings of a container port, the ones that suit the Scope are chosen from, a
   * wildcard one first, then the first in Docker's order. If none suits, all are chosen from alike.
   * Only a container port with no mapping at all falls back to the container's own address.
   *
   * @param scope that of the Service's Publication, or {@code null} when it has none
   */
  private BoundInstance boundInstance(
      String service,
      String id,
      ContainerBindingRequest binding,
      Container container,
      String lanAddress,
      Scope scope) {
    String hostAddress = scope == Scope.LOCAL ? LOOPBACK : lanAddress;
    int containerPort = binding.port();
    List<Port> mappings = container.published(containerPort);
    List<Port> suited = mappings.stream().filter(port -> suits(port, scope)).toList();
    Optional<Port> published =
        (suited.isEmpty() ? mappings : suited)
            .stream()
                .min(
                    Comparator.comparing(port ->
                        EVERY_ADDRESS.contains(port.ip() == null ? "" : port.ip()) ? 0 : 1));
    String host;
    int port;
    if (published.isPresent()) {
      String ip = published.get().ip() == null ? "" : published.get().ip();
      host = EVERY_ADDRESS.contains(ip) ? hostAddress : ip;
      port = published.get().publicPort();
    } else {
      host = container.address().orElse(hostAddress);
      port = containerPort;
    }
    return new BoundInstance(
        service,
        id,
        binding.followsCompose() ? container.name() : binding.container(),
        containerPort,
        host,
        port,
        published.isPresent() || container.address().isEmpty());
  }

  /**
   * Whether a mapping suits {@code scope}: Local takes loopback or every address, Yard takes every
   * address or one that a Route from another Workstation can reach. With no Publication, any
   * mapping suits.
   */
  private boolean suits(Port port, Scope scope) {
    String ip = port.ip() == null ? "" : port.ip();
    if (scope == null || EVERY_ADDRESS.contains(ip)) {
      return true;
    }
    return scope == Scope.LOCAL
        ? InetAddress.ofLiteral(ip).isLoopbackAddress()
        : !outOfRange.contains(ip);
  }

  private void checkHome(String workstation) {
    if (!this.workstation.is(workstation)) {
      throw new RemoteServiceException(workstation);
    }
  }
}
