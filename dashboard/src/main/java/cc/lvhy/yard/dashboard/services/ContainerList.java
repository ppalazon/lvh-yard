package cc.lvhy.yard.dashboard.services;

import cc.lvhy.yard.dashboard.docker.Docker;
import cc.lvhy.yard.dashboard.docker.DockerFailures;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Every container of this Workstation, with the Services whose Container Bindings follow it. */
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
    List<ListedContainer> listed =
        every.stream()
            .map(container ->
                ListedContainer.of(
                    container,
                    ContainerBindings.followers(
                        followed, binding -> ContainerBindings.matches(binding, container))))
            .toList();
    // A Binding follows every container of its Compose service, so the ports say it for the
    // service.
    Map<String, List<ListedContainer>> services =
        listed.stream()
            .filter(container -> container.composeProject() != null)
            .collect(Collectors.groupingBy(ContainerList::composeKey));
    return listed.stream()
        .map(container -> {
          List<ListedContainer> target =
              container.composeProject() == null
                  ? List.of(container)
                  : services.get(composeKey(container));
          return container.withPorts(
              container.ports().stream()
                  .map(port ->
                      port.following(
                          ContainerBindings.followersAt(followed, target, port.containerPort())))
                  .toList());
        })
        .sorted(
            Comparator.comparing(
                    ListedContainer::composeProject,
                    Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ListedContainer::name))
        .toList();
  }

  private static String composeKey(ListedContainer container) {
    return container.composeProject() + "/" + container.composeService();
  }

  /**
   * The Services that have a Binding that names this target, whatever its port, by name. A Binding
   * by name and a Binding by Compose service are different targets, even for one container.
   */
  public List<String> following(ContainerBindingRequest target) {
    return ContainerBindings.followers(
        bindings.all(null), binding -> ContainerBindings.sameTarget(target, binding));
  }
}
