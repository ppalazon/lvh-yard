package cc.lvhy.yard.dashboard.services;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @param remotes ordered by name
 */
public record Yard(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) HomeWorkstation home,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<YardWorkstation> remotes) {

  /** This Workstation's first, then by Workstation and name. */
  public List<Service> services() {
    return Stream.concat(
            home.services().stream(),
            remotes.stream().flatMap(remote -> remote.services().stream()))
        .toList();
  }

  /** The Service {@code service} of the other Workstation {@code workstation}, if it was read. */
  Optional<Service> remoteService(String workstation, String service) {
    return remoteService(remotes, workstation, service);
  }

  static Optional<Service> remoteService(
      List<YardWorkstation> remotes, String workstation, String service) {
    return remotes.stream()
        .filter(remote -> remote.name().equals(workstation))
        .flatMap(remote -> remote.services().stream())
        .filter(candidate -> candidate.name().equals(service))
        .findFirst();
  }
}
