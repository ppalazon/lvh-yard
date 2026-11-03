package dev.palazon.lvhyard.dashboard.status;

/**
 * A Peer from {@code YARD_PEERS}: the host its Consul answers on for the WAN pool, and the port.
 *
 * @param host an IPv4 or IPv6 address, without brackets, or a name
 * @param port its serf WAN port
 */
record Peer(String host, int port) {

  /** The port a Peer's Consul listens on for the WAN pool when its address names none. */
  static final int SERF_WAN_PORT = 8302;

  /**
   * The Peer written as {@code <host>}, {@code <host>:<port>}, {@code [<ipv6>]:<port>} or a bare
   * IPv6 address.
   *
   * @throws IllegalArgumentException when {@code peer} has a port that is not a number, or an
   *     unclosed bracket
   */
  static Peer parse(String peer) {
    if (peer.startsWith("[")) {
      int close = peer.indexOf(']');
      if (close < 0) {
        throw new IllegalArgumentException("A Peer's [ has no ]: " + peer);
      }
      String rest = peer.substring(close + 1);
      if (rest.isEmpty()) {
        return new Peer(peer.substring(1, close), SERF_WAN_PORT);
      }
      if (!rest.startsWith(":")) {
        throw new IllegalArgumentException("A Peer has more after its ]: " + peer);
      }
      return new Peer(peer.substring(1, close), port(peer, rest.substring(1)));
    }
    int colon = peer.indexOf(':');
    if (colon < 0 || colon != peer.lastIndexOf(':')) {
      // No port, or a bare IPv6 address, whose colons name no port.
      return new Peer(peer, SERF_WAN_PORT);
    }
    return new Peer(peer.substring(0, colon), port(peer, peer.substring(colon + 1)));
  }

  private static int port(String peer, String port) {
    try {
      return Integer.parseInt(port);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("A Peer's port is not a number: " + peer, e);
    }
  }
}
