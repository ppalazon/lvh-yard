package dev.palazon.lvhyard.dashboard.consul;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * From {@code YARD_CONSUL_HTTP_PORT} and {@code YARD_PEERS}.
 *
 * @param peers separated by spaces or commas, each {@code <ip>}, {@code <ip>:<serf-wan-port>} or
 *     {@code [<ipv6>]:<serf-wan-port>}
 */
@ConfigurationProperties("lvh-yard.consul")
public record ConsulSettings(URI url, String peers) {

  public List<String> peerAddresses() {
    return peers == null
        ? List.of()
        : Arrays.stream(peers.split("[\\s,]+")).filter(peer -> !peer.isBlank()).toList();
  }
}
