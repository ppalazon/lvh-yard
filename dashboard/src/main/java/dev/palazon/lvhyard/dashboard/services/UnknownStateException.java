package dev.palazon.lvhyard.dashboard.services;

import java.util.Arrays;
import java.util.stream.Collectors;

/** No Service state has this name. */
public class UnknownStateException extends RuntimeException {

  UnknownStateException(String state) {
    super("The state is " + names() + ", not " + state + ".");
  }

  /** Every state's name, such as {@code unpublished, unavailable, unhealthy or healthy}. */
  private static String names() {
    String all =
        Arrays.stream(ServiceState.values())
            .map(ServiceState::value)
            .collect(Collectors.joining(", "));
    int last = all.lastIndexOf(", ");
    return all.substring(0, last) + " or " + all.substring(last + 2);
  }
}
