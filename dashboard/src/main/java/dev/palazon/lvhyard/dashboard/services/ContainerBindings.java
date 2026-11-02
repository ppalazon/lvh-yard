package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.consul.Consul;
import dev.palazon.lvhyard.dashboard.docker.Docker;
import dev.palazon.lvhyard.dashboard.docker.Docker.Container;
import dev.palazon.lvhyard.dashboard.docker.Docker.Port;
import dev.palazon.lvhyard.dashboard.services.Registrations.BoundInstance;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * This Workstation's Container Bindings, in its Consul KV: one key per Binding, under its Service,
 * holding the container's name, or the Compose project and service, and the port as JSON. It keeps
 * the Bound Instances in step with the containers running now; the {@link ContainerWatcher} tells
 * it when they may have changed.
 */
@Component
public class ContainerBindings {

  private static final Logger LOG = LoggerFactory.getLogger(ContainerBindings.class);

  /** Where the Container Bindings live in the KV store, under LVH Yard's own prefix. */
  private static final String PREFIX = "lvh-yard/container-bindings/";

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

  private static final ObjectMapper JSON = JsonMapper.builder().build();

  private final Workstation workstation;

  private final Consul consul;

  private final Docker docker;

  private final Registrations registrations;

  ContainerBindings(
      Workstation workstation, Consul consul, Docker docker, Registrations registrations) {
    this.workstation = workstation;
    this.consul = consul;
    this.docker = docker;
    this.registrations = registrations;
  }

  /**
   * Binds {@code service} of {@code workstation}, which must be this one, to the container {@code
   * request} names, or to its Compose project and service, and its port. Binding the same again
   * changes nothing. The containers that match and run now give the Service their Bound Instances
   * at once.
   *
   * @return the Binding's ID
   */
  public String add(String workstation, String service, ContainerBindingRequest request) {
    checkHome(workstation);
    Registrations.checkName(service);
    ContainerBindingRequest binding = checked(request);
    String id = ContainerBinding.id(binding);
    consul.putValue(PREFIX + service + "/" + id, JSON.writeValueAsString(binding));
    syncIfDockerAnswers();
    return id;
  }

  /**
   * {@code request} with its names stripped, once it names a container, or a Compose project and
   * service.
   */
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

  /** {@code name} without the spaces around it; {@code null} when nothing is left. */
  private static String stripped(String name) {
    return name == null || name.isBlank() ? null : name.strip();
  }

  /**
   * Removes the Container Binding {@code id} of {@code service} of {@code workstation}, which must
   * be this one, and with it its Bound Instances, whether Docker answers or not.
   *
   * @throws UnknownContainerBindingException when the Service has no such Binding
   */
  public void remove(String workstation, String service, String id) {
    checkHome(workstation);
    String key = PREFIX + service + "/" + id;
    if (!consul.values(null, PREFIX + service + "/").containsKey(key)) {
      throw new UnknownContainerBindingException(service, id);
    }
    consul.deleteValue(key);
    registrations.removeBoundInstances(service, id);
  }

  /**
   * The Container Bindings in {@code datacenter}'s KV, by Service name, each as it is kept: those
   * of the Workstation it belongs to, or this one's when it is {@code null}. Each Service's come by
   * ID.
   */
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
   * Brings every Bound Instance in step with the containers running now: each running container a
   * Binding matches gives it a Bound Instance, at its current address, and every other Bound
   * Instance is removed.
   *
   * @throws RuntimeException when Docker or Consul does not answer, which leaves the Bound
   *     Instances as they were
   */
  public synchronized void sync() {
    List<Container> running = docker.runningContainers();
    String lanAddress = registrations.defaultHost();
    List<BoundInstance> wanted = new ArrayList<>();
    all(null)
        .forEach((service, bindings) ->
            bindings.forEach((id, binding) ->
                running.stream()
                    .filter(container -> matches(binding, container))
                    .forEach(container ->
                        wanted.add(boundInstance(service, id, binding, container, lanAddress)))));
    registrations.keepBoundInstances(wanted);
  }

  /**
   * Whether {@code container} is the one {@code binding} names, or one of its Compose service's.
   */
  private static boolean matches(ContainerBindingRequest binding, Container container) {
    return binding.followsCompose()
        ? container.isOfComposeService(binding.composeProject(), binding.composeService())
        : container.isNamed(binding.container());
  }

  /**
   * Syncs after a change to the Bindings; a Docker that does not answer leaves it to the watcher,
   * later.
   */
  private void syncIfDockerAnswers() {
    try {
      sync();
    } catch (RuntimeException e) {
      LOG.warn("The Bound Instances cannot be brought in step with Docker now: {}", e.getMessage());
    }
  }

  /**
   * Where {@code container} gives {@code service} its Bound Instance. A container port published on
   * every host address is at the LAN address and the host port, so other Workstations reach it; one
   * published on one address only is at that address. Otherwise it is at the container's own
   * address and the container port, or, for a container on the host's network, at the LAN address.
   */
  private static BoundInstance boundInstance(
      String service,
      String id,
      ContainerBindingRequest binding,
      Container container,
      String lanAddress) {
    int containerPort = binding.port();
    Optional<Port> published =
        container.published(containerPort).stream()
            .min(
                Comparator.comparing(
                    port -> EVERY_ADDRESS.contains(port.ip() == null ? "" : port.ip()) ? 0 : 1));
    String host;
    int port;
    if (published.isPresent()) {
      String ip = published.get().ip() == null ? "" : published.get().ip();
      host = EVERY_ADDRESS.contains(ip) ? lanAddress : ip;
      port = published.get().publicPort();
    } else {
      host = container.address().orElse(lanAddress);
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

  private void checkHome(String workstation) {
    if (!this.workstation.is(workstation)) {
      throw new RemoteServiceException(workstation);
    }
  }
}
