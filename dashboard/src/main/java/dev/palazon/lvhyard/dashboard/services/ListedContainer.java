package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import dev.palazon.lvhyard.dashboard.docker.Docker;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A container on this Workstation, running or stopped.
 *
 * @param composeProject none unless the container has both Compose labels, as a Binding by Compose
 *     needs the service too
 * @param service the proposed Service name: the Compose service or the container name, cleaned to
 *     follow the Service name rule
 * @param ports its TCP port mappings, each once. Docker lists none for a stopped container
 * @param health none when it has no HEALTHCHECK
 * @param followedBy the Service of a Container Binding that matches it, whatever the Binding's
 *     port, or none
 */
public record ListedContainer(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @JsonInclude(JsonInclude.Include.NON_NULL) String composeProject,
    @JsonInclude(JsonInclude.Include.NON_NULL) String composeService,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String service,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean running,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ContainerPort> ports,
    @JsonInclude(JsonInclude.Include.NON_NULL) HealthcheckState health,
    @JsonInclude(JsonInclude.Include.NON_NULL) String followedBy) {

  /** {@code hostPort} and {@code hostIp} are none when the container port is not published. */
  public record ContainerPort(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int containerPort,
      @JsonInclude(JsonInclude.Include.NON_NULL) Integer hostPort,
      @JsonInclude(JsonInclude.Include.NON_NULL) String hostIp) {}

  /** Its Docker HEALTHCHECK state. */
  public enum HealthcheckState {
    STARTING,
    HEALTHY,
    UNHEALTHY;

    @JsonValue
    public String value() {
      return name().toLowerCase(Locale.ROOT);
    }

    static Optional<HealthcheckState> of(String value) {
      return Arrays.stream(values()).filter(state -> state.value().equals(value)).findFirst();
    }
  }

  static ListedContainer of(Docker.Container container, String followedBy) {
    Optional<String> composeService =
        container.composeProject().isPresent() ? container.composeService() : Optional.empty();
    return new ListedContainer(
        container.name(),
        composeService.flatMap(service -> container.composeProject()).orElse(null),
        composeService.orElse(null),
        proposedService(composeService, container.name()),
        container.running(),
        container.ports().stream()
            .filter(port -> "tcp".equals(port.type()))
            .map(port ->
                new ContainerPort(
                    port.privatePort(),
                    port.publicPort(),
                    port.publicPort() == null ? null : port.ip()))
            .distinct()
            .sorted(
                Comparator.comparingInt(ContainerPort::containerPort)
                    .thenComparing(
                        ContainerPort::hostPort, Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(
                        ContainerPort::hostIp, Comparator.nullsFirst(Comparator.naturalOrder())))
            .toList(),
        container.healthState().flatMap(HealthcheckState::of).orElse(null),
        followedBy);
  }

  /** A port published on IPv4 and IPv6 has two mappings in {@link #ports}. */
  public List<Integer> containerPorts() {
    return ports.stream().map(ContainerPort::containerPort).distinct().toList();
  }

  /**
   * The Compose service cleaned, else the container name cleaned. A Docker container name starts
   * with a letter or a digit, so it is never cleaned to nothing. The fixed name is for safety.
   */
  static String proposedService(Optional<String> composeService, String container) {
    return Stream.concat(composeService.stream(), Stream.of(container))
        .map(ListedContainer::cleaned)
        .filter(Registrations::isDnsLabel)
        .findFirst()
        .orElse("container");
  }

  /**
   * Lower case, with {@code _}, {@code .} and other characters a DNS label cannot hold turned into
   * {@code -}, and no {@code -} at either end.
   */
  private static String cleaned(String name) {
    String cleaned = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
    if (cleaned.length() > Registrations.MAX_NAME_LENGTH) {
      cleaned = cleaned.substring(0, Registrations.MAX_NAME_LENGTH);
    }
    return cleaned.replaceAll("^-+|-+$", "");
  }
}
