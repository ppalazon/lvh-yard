package cc.lvhy.yard.dashboard;

/**
 * Where this Workstation reaches a server that listens on a bind address. compose.yaml's Traefik
 * command follows the same rules.
 */
public final class BindAddress {

  private BindAddress() {}

  /**
   * Loopback for the wildcard {@code 0.0.0.0}, which listens there too. Any other address stays as
   * it is.
   */
  public static String host(String bindAddress) {
    return bindAddress.equals("0.0.0.0") ? "127.0.0.1" : bindAddress;
  }
}
