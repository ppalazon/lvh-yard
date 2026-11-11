package cc.lvhy.yard.dashboard.profiles;

import java.util.List;

/** A Publication or a chain names the profile, so it stays. */
public class RouteProfileInUseException extends RuntimeException {

  private final List<String> services;

  private final List<String> chains;

  RouteProfileInUseException(String profile, List<String> services, List<String> chains) {
    super(message(profile, services, chains));
    this.services = List.copyOf(services);
    this.chains = List.copyOf(chains);
  }

  public List<String> services() {
    return services;
  }

  public List<String> chains() {
    return chains;
  }

  private static String message(String profile, List<String> services, List<String> chains) {
    StringBuilder message =
        new StringBuilder(profile).append(" is in use, so it cannot be removed.");
    if (!services.isEmpty()) {
      message
          .append(" The Publications of these Services name it: ")
          .append(String.join(", ", services))
          .append(".");
    }
    if (!chains.isEmpty()) {
      message.append(" These chains name it: ").append(String.join(", ", chains)).append(".");
    }
    return message.toString();
  }
}
