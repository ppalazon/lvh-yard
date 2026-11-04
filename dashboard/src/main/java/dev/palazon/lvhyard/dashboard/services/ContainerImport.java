package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.docker.Docker;
import dev.palazon.lvhyard.dashboard.docker.DockerFailures;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Running containers that no Container Binding follows yet, to give them a Service. */
@Component
public class ContainerImport {

  private final Docker docker;

  private final DockerFailures failures;

  private final ContainerBindings bindings;

  ContainerImport(Docker docker, DockerFailures failures, ContainerBindings bindings) {
    this.docker = docker;
    this.failures = failures;
    this.bindings = bindings;
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

  private List<Docker.Container> running() {
    try {
      return docker.runningContainers();
    } catch (RuntimeException e) {
      throw new UnreadableDockerException(failures.reason(e), e);
    }
  }
}
