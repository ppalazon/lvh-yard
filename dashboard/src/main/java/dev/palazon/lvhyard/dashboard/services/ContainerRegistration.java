package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.docker.Docker;
import dev.palazon.lvhyard.dashboard.docker.DockerFailures;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Containers that no Container Binding follows yet, to give them a Service. */
@Component
public class ContainerRegistration {

  private final Docker docker;

  private final DockerFailures failures;

  private final ContainerBindings bindings;

  private final Workstation workstation;

  private final Publications publications;

  private final YardReader yard;

  ContainerRegistration(
      Docker docker,
      DockerFailures failures,
      ContainerBindings bindings,
      Workstation workstation,
      Publications publications,
      YardReader yard) {
    this.docker = docker;
    this.failures = failures;
    this.bindings = bindings;
    this.workstation = workstation;
    this.publications = publications;
    this.yard = yard;
  }

  /**
   * A container that any Binding of this Workstation matches is left out, whatever the Binding's
   * port. Compose projects come first, by name, then the other containers.
   *
   * @throws UnreadableDockerException when Docker does not answer
   */
  public List<UnfollowedContainer> unfollowed() {
    List<Docker.Container> running = running();
    Map<String, Map<String, ContainerBindingRequest>> followed = bindings.all(null);
    return running.stream()
        .filter(container ->
            ContainerBindings.following(
                    followed, binding -> ContainerBindings.matches(binding, container))
                .isEmpty())
        .map(UnfollowedContainer::of)
        .sorted(
            Comparator.comparing(
                    UnfollowedContainer::composeProject,
                    Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(UnfollowedContainer::name))
        .toList();
  }

  /**
   * Binds the container {@code name} to a Service. A running container is bound by its Compose
   * project and service when it has them, so the Binding outlives {@code docker compose up}, else
   * by its name. A container that does not run is bound by its name, so the Binding can come before
   * the container. A Service that already exists keeps its Publication, or stays Unpublished. The
   * Service name is by default the proposed one, the Compose service or container name cleaned.
   *
   * @return the Service, with the container bound
   * @throws FollowedContainerException when a Container Binding already follows the container
   * @throws InvalidRegistrationException when the Service name or the port breaks a rule
   * @throws UnreadableDockerException when Docker does not answer
   */
  public Service register(String name, ContainerRegistrationRequest request) {
    return register(name, request, true);
  }

  /**
   * Binds only the container {@code name}, even when it has Compose labels. The Service name is by
   * default the cleaned container name. Port and Publication rules are the same as for {@link
   * #register(String, ContainerRegistrationRequest)}.
   */
  public Service registerByName(String name, ContainerRegistrationRequest request) {
    return register(name, request, false);
  }

  private Service register(
      String name, ContainerRegistrationRequest request, boolean followCompose) {
    Optional<Docker.Container> running =
        running().stream().filter(container -> container.isNamed(name)).findFirst();
    Map<String, Map<String, ContainerBindingRequest>> followed = bindings.all(null);
    ContainerBindings.following(
            followed,
            binding ->
                running
                    .map(container -> ContainerBindings.matches(binding, container))
                    .orElseGet(() -> name.equals(binding.container())))
        .ifPresent(following -> {
          throw new FollowedContainerException(name, following);
        });
    String proposed;
    ContainerBindingRequest binding;
    if (running.isPresent()) {
      UnfollowedContainer unfollowed = UnfollowedContainer.of(running.get());
      proposed =
          followCompose
              ? unfollowed.service()
              : UnfollowedContainer.proposedService(Optional.empty(), name);
      int port = request.port() == null ? onlyPort(unfollowed) : request.port();
      binding =
          !followCompose || unfollowed.composeProject() == null
              ? ContainerBindingRequest.container(name, port)
              : ContainerBindingRequest.compose(
                  unfollowed.composeProject(), unfollowed.composeService(), port);
    } else {
      if (request.port() == null) {
        throw new InvalidRegistrationException(
            "port", "The container " + name + " does not run: give the port.");
      }
      proposed = UnfollowedContainer.proposedService(Optional.empty(), name);
      binding = ContainerBindingRequest.container(name, request.port());
    }
    String service =
        request.service() == null || request.service().isBlank()
            ? proposed
            : request.service().strip();
    publications.addPublishingNew(
        service, request.publish(), () -> bindings.add(workstation.name(), service, binding));
    return yard.service(workstation.name(), service).orElseThrow();
  }

  private static int onlyPort(UnfollowedContainer container) {
    List<Integer> ports = container.containerPorts();
    if (ports.size() == 1) {
      return ports.getFirst();
    }
    throw new InvalidRegistrationException(
        "port",
        ports.isEmpty()
            ? "The container " + container.name() + " has no exposed port: give the port."
            : "The container "
                + container.name()
                + " has more than one port: give one of "
                + container.containerPortList()
                + ".");
  }

  private List<Docker.Container> running() {
    try {
      return docker.runningContainers();
    } catch (RuntimeException e) {
      throw new UnreadableDockerException(failures.reason(e), e);
    }
  }
}
