package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Also how a Binding is kept in Consul KV, without {@code publish}. It names either a container, or
 * a Compose project and service.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContainerBindingRequest(
    String container,
    String composeProject,
    String composeService,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer port,
    @Schema(
            description =
                "If the Service is new, false leaves it Unpublished. Otherwise it gets a"
                    + " Publication with Local Scope. An existing Service keeps its Publication, or"
                    + " stays Unpublished.")
        Boolean publish) {

  public static ContainerBindingRequest container(String container, Integer port) {
    return new ContainerBindingRequest(container, null, null, port, null);
  }

  public static ContainerBindingRequest compose(
      String composeProject, String composeService, Integer port) {
    return new ContainerBindingRequest(null, composeProject, composeService, port, null);
  }

  boolean followsCompose() {
    return composeProject != null || composeService != null;
  }
}
