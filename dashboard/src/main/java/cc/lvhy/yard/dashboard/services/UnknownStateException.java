package cc.lvhy.yard.dashboard.services;

import java.util.Arrays;
import java.util.stream.Collectors;

public class UnknownStateException extends RuntimeException {

  UnknownStateException(String state) {
    super("The state is " + names() + ", not " + state + ".");
  }

  private static String names() {
    String all =
        Arrays.stream(ServiceState.values())
            .map(ServiceState::value)
            .collect(Collectors.joining(", "));
    int last = all.lastIndexOf(", ");
    return all.substring(0, last) + " or " + all.substring(last + 2);
  }
}
