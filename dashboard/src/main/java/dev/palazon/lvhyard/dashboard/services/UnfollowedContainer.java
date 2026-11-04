package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.palazon.lvhyard.dashboard.docker.Docker;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * A running container on this Workstation that no Container Binding follows.
 *
 * @param service the proposed Service name: the Compose service or the container name, cleaned to
 *     follow the Service name rule
 * @param ports its TCP ports, each once
 * @param health its HEALTHCHECK state, none when it has no HEALTHCHECK
 */
public record UnfollowedContainer(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @JsonInclude(JsonInclude.Include.NON_NULL) String composeProject,
    @JsonInclude(JsonInclude.Include.NON_NULL) String composeService,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String service,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ContainerPort> ports,
    @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(allowableValues = {"starting", "healthy", "unhealthy"})
        String health) {

  /** {@code hostPort} is none when the container port is not published. */
  public record ContainerPort(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int containerPort,
      @JsonInclude(JsonInclude.Include.NON_NULL) Integer hostPort) {}

  static UnfollowedContainer of(Docker.Container container) {
    return new UnfollowedContainer(
        container.name(),
        container.composeProject().orElse(null),
        container.composeService().orElse(null),
        serviceName(container.composeService().orElse(container.name())),
        container.ports().stream()
            .filter(port -> "tcp".equals(port.type()))
            .map(port -> new ContainerPort(port.privatePort(), port.publicPort()))
            .distinct()
            .sorted(
                Comparator.comparingInt(ContainerPort::containerPort)
                    .thenComparing(
                        ContainerPort::hostPort, Comparator.nullsFirst(Comparator.naturalOrder())))
            .toList(),
        container.healthState().orElse(null));
  }

  /**
   * Lower case, with {@code _}, {@code .} and other characters a DNS label cannot hold turned into
   * {@code -}, and no {@code -} at either end.
   */
  static String serviceName(String name) {
    String cleaned = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
    if (cleaned.length() > 63) {
      cleaned = cleaned.substring(0, 63);
    }
    return cleaned.replaceAll("^-+|-+$", "");
  }
}
