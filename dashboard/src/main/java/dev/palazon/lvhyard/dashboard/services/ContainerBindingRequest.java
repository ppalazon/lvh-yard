package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A Container Binding to add, which is also how one is kept in Consul KV. It names either a
 * container, or a Compose project and service.
 *
 * @param container the name of the container to follow, as {@code docker run --name} takes it;
 *     {@code null} for a Compose service
 * @param composeProject the Compose project whose service to follow; {@code null} for a container
 *     by name
 * @param composeService the Compose service to follow, in that project; {@code null} for a
 *     container by name
 * @param port the container port the Service listens on, inside the container
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContainerBindingRequest(
    String container,
    String composeProject,
    String composeService,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer port) {

  /** A Binding to the container called {@code container}. */
  public static ContainerBindingRequest container(String container, Integer port) {
    return new ContainerBindingRequest(container, null, null, port);
  }

  /** A Binding to every container of {@code composeService} in {@code composeProject}. */
  public static ContainerBindingRequest compose(
      String composeProject, String composeService, Integer port) {
    return new ContainerBindingRequest(null, composeProject, composeService, port);
  }

  /** Whether it follows a Compose service, rather than a container by name. */
  boolean followsCompose() {
    return composeProject != null || composeService != null;
  }
}
