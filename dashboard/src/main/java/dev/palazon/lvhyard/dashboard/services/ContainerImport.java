package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.docker.Docker;
import dev.palazon.lvhyard.dashboard.docker.DockerFailures;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Running containers that no Container Binding follows yet, to give them a Service. */
@Component
public class ContainerImport {

  private final Docker docker;

  private final DockerFailures failures;

  private final ContainerBindings bindings;

  private final Workstation workstation;

  private final Publications publications;

  private final YardReader yard;

  ContainerImport(
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
    List<ContainerBindingRequest> followed =
        bindings.all(null).values().stream().flatMap(byId -> byId.values().stream()).toList();
    return running.stream()
        .filter(container ->
            followed.stream().noneMatch(binding -> ContainerBindings.matches(binding, container)))
        .map(UnfollowedContainer::of)
        .sorted(
            Comparator.comparing(
                    UnfollowedContainer::composeProject,
                    Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(UnfollowedContainer::name))
        .toList();
  }

  /**
   * Binds the running container {@code name} to a Service: by its Compose project and service when
   * it has them, so the Binding outlives {@code docker compose up}, else by its name. A Service
   * that already exists keeps its Publication, or stays unpublished. The Service name is by default
   * the proposed one, the Compose service or container name cleaned.
   *
   * @return the Service, with the container bound
   * @throws UnknownContainerException when no running container has the name
   * @throws InvalidRegistrationException when the Service name or the port breaks a rule
   * @throws UnreadableDockerException when Docker does not answer
   */
  public Service importContainer(String name, ContainerImportRequest request) {
    Docker.Container container =
        running().stream()
            .filter(running -> running.isNamed(name))
            .findFirst()
            .orElseThrow(() -> new UnknownContainerException(name));
    UnfollowedContainer unfollowed = UnfollowedContainer.of(container);
    String service =
        request.service() == null || request.service().isBlank()
            ? unfollowed.service()
            : request.service().strip();
    int port = request.port() == null ? onlyPort(unfollowed) : request.port();
    boolean existed = publications.exists(service);
    bindings.add(
        workstation.name(),
        service,
        container
            .composeProject()
            .map(project ->
                ContainerBindingRequest.compose(
                    project, container.composeService().orElseThrow(), port))
            .orElseGet(() -> ContainerBindingRequest.container(name, port)));
    if (!existed && !Boolean.FALSE.equals(request.publish())) {
      publications.publish(workstation.name(), service, null, null);
    }
    return yard.service(workstation.name(), service).orElseThrow();
  }

  private static int onlyPort(UnfollowedContainer container) {
    List<Integer> ports =
        container.ports().stream()
            .map(UnfollowedContainer.ContainerPort::containerPort)
            .distinct()
            .toList();
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
                + ports.stream().map(String::valueOf).collect(Collectors.joining(", "))
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
