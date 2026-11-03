package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.BindAddress;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

/**
 * A Core Service: one of the pieces LVH Yard runs on, which the dashboard keeps registered and
 * published on its own Workstation. Each has a Static Instance on this Workstation, with an HTTP
 * check on {@link #path}, and its name is taken: no other Service or Alias can have it.
 */
public enum CoreService {
  /** The dashboard, where it listens; on {@code 0.0.0.0}, it is reached at loopback. */
  YARD(
      "yard",
      "the dashboard",
      "/actuator/health",
      settings ->
          new Location(BindAddress.host(settings.dashboardAddress()), settings.dashboardPort())),

  /** Consul's web page and HTTP API. */
  CONSUL(
      "consul",
      "Consul's web page",
      "/v1/status/leader",
      settings -> Location.loopback(settings.consulPort())),

  /** Traefik's admin page, on its loopback entry point. */
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

  /** Its Service name, which is also its hostname's first part: {@code yard} for the dashboard. */
  public String serviceName() {
    return serviceName;
  }

  /** What it is, in words: "the dashboard" for {@code yard}. */
  String what() {
    return what;
  }

  /** The path its HTTP check GETs. */
  String path() {
    return path;
  }

  /** Where its Static Instance is, as this Workstation's {@code settings} place it. */
  Location location(Settings settings) {
    return location.apply(settings);
  }

  /** The Core Service named {@code name}, if one is. */
  static Optional<CoreService> named(String name) {
    return Arrays.stream(values()).filter(core -> core.serviceName.equals(name)).findFirst();
  }

  /** Whether {@code name} is a Core Service's, so no other Service can take it. */
  public static boolean isNamed(String name) {
    return named(name).isPresent();
  }

  /** Where a Core Service's Static Instance is. */
  record Location(String host, int port) {

    /** {@code port} on loopback, so only its own Workstation reaches it. */
    static Location loopback(int port) {
      return new Location("127.0.0.1", port);
    }
  }

  /**
   * This Workstation's settings that place the Core Services.
   *
   * @param dashboardAddress the address the dashboard listens on
   * @param dashboardPort the port the dashboard listens on
   * @param consulPort the port of Consul's HTTP API
   * @param traefikAdminPort the port of Traefik's admin entry point
   */
  record Settings(
      String dashboardAddress, int dashboardPort, int consulPort, int traefikAdminPort) {}
}
