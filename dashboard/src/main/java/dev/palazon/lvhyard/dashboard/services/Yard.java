package dev.palazon.lvhyard.dashboard.services;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
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
}
