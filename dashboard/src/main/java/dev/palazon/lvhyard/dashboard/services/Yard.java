package dev.palazon.lvhyard.dashboard.services;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.stream.Stream;

/**
 * The Yard as this Workstation sees it.
 *
 * @param home this Workstation, with its own Services
 * @param remotes every other Workstation, ordered by name, with its Remote Services
 */
public record Yard(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) HomeWorkstation home,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<YardWorkstation> remotes) {

  /** Every Service known: this Workstation's first, then the others' by Workstation and name. */
  public List<Service> services() {
    return Stream.concat(
            home.services().stream(),
            remotes.stream().flatMap(remote -> remote.services().stream()))
        .toList();
  }
}
