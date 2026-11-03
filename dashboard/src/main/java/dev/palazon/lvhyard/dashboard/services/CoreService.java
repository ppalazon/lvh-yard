package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.BindAddress;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

/**
 * The dashboard keeps each one registered and published on its own Workstation. Its name is taken:
 * no other Service or Alias can have it.
 */
public enum CoreService {
  /** On {@code 0.0.0.0}, it is reached at loopback. */
  YARD(
      "yard",
      "the dashboard",
      "/actuator/health",
      settings ->
          new Location(BindAddress.host(settings.dashboardAddress()), settings.dashboardPort())),

  CONSUL(
      "consul",
      "Consul's web page",
      "/v1/status/leader",
      settings -> Location.loopback(settings.consulPort())),

  /** On its loopback entry point. */
  TRAEFIK(
      "traefik",
      "Traefik's admin page",
      "/ping",
      settings -> Location.loopback(settings.traefikAdminPort()));

  private final String serviceName;

  private final String what;

  private final String path;

  private final Function<Settings, Location> location;

  CoreService(String serviceName, String what, String path, Function<Settings, Location> location) {
    this.serviceName = serviceName;
    this.what = what;
    this.path = path;
    this.location = location;
  }

  /** Also the first part of its hostname. */
  public String serviceName() {
    return serviceName;
  }

  public String what() {
    return what;
  }

  String path() {
    return path;
  }

  Location location(Settings settings) {
    return location.apply(settings);
  }

  static Optional<CoreService> named(String name) {
    return Arrays.stream(values()).filter(core -> core.serviceName.equals(name)).findFirst();
  }

  public static boolean isNamed(String name) {
    return named(name).isPresent();
  }

  record Location(String host, int port) {

    /** So only its own Workstation reaches it. */
    static Location loopback(int port) {
      return new Location("127.0.0.1", port);
    }
  }

  record Settings(
      String dashboardAddress, int dashboardPort, int consulPort, int traefikAdminPort) {}
}
