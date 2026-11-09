package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.docker.Docker;
import dev.palazon.lvhyard.dashboard.docker.DockerFailures;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Every container of this Workstation, with the Container Binding that follows it. */
@Component
public class ContainerList {

  private final Docker docker;

  private final DockerFailures failures;

  private final ContainerBindings bindings;

  ContainerList(Docker docker, DockerFailures failures, ContainerBindings bindings) {
    this.docker = docker;
    this.failures = failures;
    this.bindings = bindings;
  }

  /**
   * Stopped containers too. Compose projects come first, by name, then the other containers.
   *
   * @throws UnreadableDockerException when Docker does not answer
   */
  public List<ListedContainer> containers() {
    List<Docker.Container> every;
    try {
      every = docker.containers();
    } catch (RuntimeException e) {
      throw new UnreadableDockerException(failures.reason(e), e);
    }
    Map<String, Map<String, ContainerBindingRequest>> followed = bindings.all(null);
    return every.stream()
        .map(container ->
            ListedContainer.of(
                container,
                ContainerBindings.following(
                        followed, binding -> ContainerBindings.matches(binding, container))
                    .orElse(null)))
        .sorted(
            Comparator.comparing(
                    ListedContainer::composeProject,
                    Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ListedContainer::name))
        .toList();
  }

  /**
   * The Service of the Binding that names this target, whatever its port, or {@code null}. A
   * Binding by name and a Binding by Compose service are different targets, even for one container.
   */
  public String following(ContainerBindingRequest target) {
    return ContainerBindings.following(
            bindings.all(null), binding -> ContainerBindings.sameTarget(target, binding))
        .orElse(null);
  }
}
