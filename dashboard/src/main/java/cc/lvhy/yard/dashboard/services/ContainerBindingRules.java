package cc.lvhy.yard.dashboard.services;

import cc.lvhy.yard.dashboard.docker.Docker.Container;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The rules of a Container Binding that need no Consul, no Docker and no Spring: what its input may
 * be, which containers it follows and which Services follow a target.
 */
public final class ContainerBindingRules {

  /** A container name, as Docker allows them. */
  private static final Pattern CONTAINER_NAME = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9_.-]{1,254}");

  /** A Compose project name, as Compose allows them. */
  private static final Pattern COMPOSE_PROJECT = Pattern.compile("[a-z0-9][a-z0-9_-]*");

  /** A Compose service name, as Compose allows them. */
  private static final Pattern COMPOSE_SERVICE = Pattern.compile("[a-zA-Z0-9._-]+");

  private ContainerBindingRules() {}

  /**
   * The Binding as {@link ContainerBindings#add} would store it. It writes nothing.
   *
   * @throws InvalidRegistrationException at the first rule that {@code request} breaks
   */
  public static ContainerBindingRequest check(String service, ContainerBindingRequest request) {
    InstanceRules.checkName(service);
    return checked(request);
  }

  /** Strips the names, and fails unless it names a container, or a Compose project and service. */
  static ContainerBindingRequest checked(ContainerBindingRequest request) {
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
      InstanceRules.checkPort("containerPort", "A Container Binding", request.port());
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
    InstanceRules.checkPort("containerPort", "A Container Binding", request.port());
    return ContainerBindingRequest.compose(project, composeService, request.port());
  }

  static boolean sameTarget(ContainerBindingRequest one, ContainerBindingRequest other) {
    return one.followsCompose()
        ? one.composeProject().equals(other.composeProject())
            && one.composeService().equals(other.composeService())
        : one.container().equals(other.container());
  }

  /**
   * The Services that have a Binding at {@code port} that matches any of {@code containers}, by
   * name. A Binding by name matches that container. A Binding by Compose service matches every
   * container of it.
   */
  static List<String> followersAt(
      Map<String, Map<String, ContainerBindingRequest>> followed,
      Collection<ListedContainer> containers,
      int port) {
    return followers(
        followed,
        binding ->
            Objects.equals(binding.port(), port)
                && containers.stream().anyMatch(container -> matches(binding, container)));
  }

  private static boolean matches(ContainerBindingRequest binding, ListedContainer container) {
    return binding.followsCompose()
        ? binding.composeProject().equals(container.composeProject())
            && binding.composeService().equals(container.composeService())
        : binding.container().equals(container.name());
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

  static boolean matches(ContainerBindingRequest binding, Container container) {
    return binding.followsCompose()
        ? container.isOfComposeService(binding.composeProject(), binding.composeService())
        : container.isNamed(binding.container());
  }

  private static String stripped(String name) {
    return name == null || name.isBlank() ? null : name.strip();
  }
}
