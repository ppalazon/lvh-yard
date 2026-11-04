package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * {@code id} is {@code <container>:<port>} or {@code compose:<project>:<service>:<port>}. The
 * fields of the other kind are left out of the JSON.
 */
public record ContainerBinding(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
    @JsonInclude(JsonInclude.Include.NON_NULL) String container,
    @JsonInclude(JsonInclude.Include.NON_NULL) String composeProject,
    @JsonInclude(JsonInclude.Include.NON_NULL) String composeService,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int port,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> instances) {

  ContainerBinding(String id, ContainerBindingRequest binding, List<String> instances) {
    this(
        id,
        binding.container(),
        binding.composeProject(),
        binding.composeService(),
        binding.port(),
        instances);
  }

  static String id(ContainerBindingRequest binding) {
    return binding.followsCompose()
        ? "compose:"
            + binding.composeProject()
            + ":"
            + binding.composeService()
            + ":"
            + binding.port()
        : binding.container() + ":" + binding.port();
  }

  /**
   * As the pages say it: {@code container web-1}, or {@code Compose service web of project shop}.
   */
  public String follows() {
    return ContainerBindingRequest.follows(container, composeProject, composeService);
  }
}
