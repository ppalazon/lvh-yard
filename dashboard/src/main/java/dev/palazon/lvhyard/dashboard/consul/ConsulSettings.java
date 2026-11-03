package dev.palazon.lvhyard.dashboard.consul;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where this Workstation's Consul agent answers, from {@code YARD_CONSUL_HTTP_PORT}, and the Peers
 * it joins, from {@code YARD_PEERS}.
 *
 * @param url the agent's HTTP API, on loopback
 * @param peers the Peers' LAN addresses, separated by spaces or commas, each as {@code <ip>} or
 *     {@code <ip>:<serf-wan-port>}; none for the first Workstation of a Yard
 */
@ConfigurationProperties("lvh-yard.consul")
public record ConsulSettings(URI url, String peers) {

  /** The Peers, one address each, as {@code YARD_PEERS} lists them. */
  public List<String> peerAddresses() {
    return peers == null
        ? List.of()
        : Arrays.stream(peers.split("[\\s,]+")).filter(peer -> !peer.isBlank()).toList();
  }
}
