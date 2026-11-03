package dev.palazon.lvhyard.dashboard.consul;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * From {@code YARD_CONSUL_HTTP_PORT} and {@code YARD_PEERS}.
 *
 * @param peers separated by spaces or commas, each {@code <ip>}, {@code <ip>:<serf-wan-port>} or
 *     {@code [<ipv6>]:<serf-wan-port>}
 * @param answerTimeout how long an answer of Consul may take. A datacenter on a computer that is
 *     switched off never answers, and the agent waits well over a minute before it gives up on it.
 * @param unreachablePause how long another Workstation is not asked, after two reads in a row found
 *     it unreachable. Only the tests set it and {@code answerTimeout}, so that they wait less.
 */
@ConfigurationProperties("lvh-yard.consul")
public record ConsulSettings(
    URI url,
    String peers,
    @DefaultValue("3s") Duration answerTimeout,
    @DefaultValue("10s") Duration unreachablePause) {

  public List<String> peerAddresses() {
    return peers == null
        ? List.of()
        : Arrays.stream(peers.split("[\\s,]+")).filter(peer -> !peer.isBlank()).toList();
  }
}
