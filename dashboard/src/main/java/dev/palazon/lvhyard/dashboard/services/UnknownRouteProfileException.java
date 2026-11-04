package dev.palazon.lvhyard.dashboard.services;

import java.util.List;

/** The message names the Route Profiles that exist, so the person can fix a typo. */
public class UnknownRouteProfileException extends RuntimeException {

  UnknownRouteProfileException(List<String> unknown, List<String> known) {
    super(
        "No Route Profile is named "
            + String.join(", ", unknown)
            + "."
            + (known.isEmpty()
                ? " route-profiles.yaml has no valid profiles."
                : " The profiles are: "
                    + String.join(", ", known.stream().sorted().toList())
                    + "."));
  }
}
