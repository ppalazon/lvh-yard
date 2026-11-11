package cc.lvhy.yard.dashboard.services;

import java.util.List;

/** The message names the Route Profiles that exist, so the person can fix a typo. */
public class UnknownRouteProfileException extends RuntimeException {

  UnknownRouteProfileException(List<String> unknown, List<String> known) {
    super(
        "No Route Profile is named "
            + String.join(", ", unknown)
            + "."
            + (known.isEmpty()
                ? " This Workstation has no Route Profiles."
                : " The profiles are: "
                    + String.join(", ", known.stream().sorted().toList())
                    + "."));
  }
}
