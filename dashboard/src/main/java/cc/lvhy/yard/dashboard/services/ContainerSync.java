package cc.lvhy.yard.dashboard.services;

import cc.lvhy.yard.dashboard.OutOfRange;
import cc.lvhy.yard.dashboard.RuntimeNetwork;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Brings the Bound Instances in step with the running containers. The {@link ContainerWatcher} says
 * when the containers may have changed. A Binding or a Publication that changes says it too.
 */
@Component
public class ContainerSync {

  private static final Logger LOG = LoggerFactory.getLogger(ContainerSync.class);

  /**
   * Host addresses that stand for every address of the host, so the LAN address reaches a port
   * published there.
   */
  private static final List<String> EVERY_ADDRESS = List.of("", "0.0.0.0", "::");

  /** The host address of a Bound Instance under Local Scope. */
  private static final String LOOPBACK = "127.0.0.1";

  private final Docker docker;

  private final ContainerBindings bindings;

  private final Registrations registrations;

  private final Publications publications;

  private final OutOfRange outOfRange;

  private final RuntimeNetwork network;

  ContainerSync(
      Docker docker,
      ContainerBindings bindings,
      Registrations registrations,
      Publications publications,
      OutOfRange outOfRange,
      RuntimeNetwork network) {
    this.docker = docker;
    this.bindings = bindings;
    this.registrations = registrations;
    this.publications = publications;
    this.outOfRange = outOfRange;
    this.network = network;
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
    bindings
        .all(null)
        .forEach((service, serviceBindings) ->
            serviceBindings.forEach((id, binding) ->
                running.stream()
                    .filter(container -> ContainerBindingRules.matches(binding, container))
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

  /** The Bound Instances follow a Publication that was added, changed or removed. */
  @EventListener
  void onPublicationsChanged(PublicationsChanged changed) {
    syncIfDockerAnswers();
  }

  /** The Bound Instances follow a Binding that was added or edited. */
  @EventListener
  void onBindingsChanged(ContainerBindingsChanged changed) {
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
   * Where {@code container} gives {@code service} its Bound Instance. A mapping on every address
   * takes the Scope's host address, so Local gives loopback and the rest the LAN address.
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
    List<Port> supported =
        network.ipv4Only()
            ? mappings.stream().filter(port -> network.supports(port.ip())).toList()
            : mappings;
    List<Port> candidates = supported.isEmpty() ? mappings : supported;
    List<Port> suited = candidates.stream().filter(port -> suits(port, scope)).toList();
    Optional<Port> published =
        (suited.isEmpty() ? candidates : suited)
            .stream().min(Comparator.comparing(port -> everyAddress(port) ? 0 : 1));
    String host;
    int port;
    if (published.isPresent()) {
      // An IPv6-only wildcard must not turn into an IPv4 target that Docker never published.
      host =
          everyAddress(published.get()) && network.supports(published.get().ip())
              ? hostAddress
              : published.get().ip();
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

  /** With no Publication, every mapping suits. */
  private boolean suits(Port port, Scope scope) {
    if (scope == null || everyAddress(port)) {
      return true;
    }
    return scope == Scope.LOCAL
        ? InetAddress.ofLiteral(port.ip()).isLoopbackAddress()
        : !outOfRange.contains(port.ip());
  }

  private static boolean everyAddress(Port port) {
    return EVERY_ADDRESS.contains(port.ip() == null ? "" : port.ip());
  }
}
