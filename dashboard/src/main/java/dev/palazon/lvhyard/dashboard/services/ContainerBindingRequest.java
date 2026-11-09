package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Optional;

/**
 * Also how a Binding is kept in Consul KV. It names either a container, or a Compose project and
 * service.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContainerBindingRequest(
    String container,
    String composeProject,
    String composeService,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer port) {

  public static ContainerBindingRequest container(String container, Integer port) {
    return new ContainerBindingRequest(container, null, null, port);
  }

  public static ContainerBindingRequest compose(
      String composeProject, String composeService, Integer port) {
    return new ContainerBindingRequest(null, composeProject, composeService, port);
  }

  boolean followsCompose() {
    return composeProject != null || composeService != null;
  }

  /**
   * The Service name to propose, as the register endpoint and the CLI do: the Compose service or
   * the container name, cleaned. {@code null} when the name it would come from is empty.
   */
  public String proposedService() {
    String target = followsCompose() ? composeService : container;
    return target == null || target.isBlank()
        ? null
        : UnfollowedContainer.proposedService(Optional.empty(), target.strip());
  }

  String follows() {
    return follows(container, composeProject, composeService);
  }

  /**
   * As the pages say it: {@code container web-1}, or {@code Compose service web of project shop}.
   */
  static String follows(String container, String composeProject, String composeService) {
    return container != null
        ? "container " + container
        : "Compose service " + composeService + " of project " + composeProject;
  }
}
