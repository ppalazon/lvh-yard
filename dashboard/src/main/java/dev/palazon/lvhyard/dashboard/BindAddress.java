package dev.palazon.lvhyard.dashboard;

/**
 * Where this Workstation reaches a server that listens on a bind address, such as Traefik on {@code
 * YARD_TRAEFIK_BIND_ADDRESS} or the dashboard on {@code YARD_DASHBOARD_BIND_ADDRESS}.
 * compose.yaml's Traefik command follows the same rules.
 */
public final class BindAddress {

  private BindAddress() {}

  /**
   * The host part of a URL to {@code bindAddress}, an IPv4 address: loopback for the wildcard
   * {@code 0.0.0.0}, which listens there too, any other address as it is.
   */
  public static String host(String bindAddress) {
    return bindAddress.equals("0.0.0.0") ? "127.0.0.1" : bindAddress;
  }
}
