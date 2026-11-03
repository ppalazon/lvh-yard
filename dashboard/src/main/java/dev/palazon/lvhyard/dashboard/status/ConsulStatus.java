package dev.palazon.lvhyard.dashboard.status;

import dev.palazon.lvhyard.dashboard.consul.Consul;
import dev.palazon.lvhyard.dashboard.consul.Consul.WanMember;
import dev.palazon.lvhyard.dashboard.consul.ConsulSettings;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * Whether this Workstation's Consul answers and has a leader, and which Workstations are in its WAN
 * pool. A Workstation there that is not alive, or a Peer from {@code YARD_PEERS} that is not there
 * at all, is a warning: a teammate's computer may simply be off, so it does not fail the status.
 */
@Component
@Order(StatusOrder.CONSUL)
class ConsulStatus implements StatusPart {

  static final String NAME = "Consul";

  /** The port a Peer's Consul listens on for the WAN pool when its address names none. */
  private static final int SERF_WAN_PORT = 8302;

  private final Consul consul;

  private final ConsulSettings settings;

  ConsulStatus(Consul consul, ConsulSettings settings) {
    this.consul = consul;
    this.settings = settings;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Part read() {
    String leader;
    List<WanMember> members;
    try {
      leader = consul.leader();
      members = consul.wanMembers();
    } catch (RestClientException e) {
      return Part.failing(
          NAME, "Consul does not answer at " + settings.url() + ": " + e.getMessage());
    }
    if (leader.isEmpty()) {
      return Part.failing(NAME, "Consul has no leader, so it cannot accept changes.");
    }
    List<Part.Detail> details = new ArrayList<>();
    details.add(Part.Detail.fact("leader", leader));
    members.stream()
        .sorted(Comparator.comparing(WanMember::datacenter))
        .forEach(member ->
            details.add(
                new Part.Detail(
                    member.datacenter(),
                    member.state() + " at " + member.address() + ":" + member.port(),
                    member.alive() ? PartState.OK : PartState.WARNING)));
    List<String> missing =
        settings.peerAddresses().stream().filter(peer -> !inPool(peer, members)).toList();
    missing.forEach(peer ->
        details.add(new Part.Detail("peer " + peer, "not in the WAN pool", PartState.WARNING)));
    return Part.of(NAME, reason(members, missing), details);
  }

  private static String reason(List<WanMember> members, List<String> missing) {
    long notAlive = members.stream().filter(member -> !member.alive()).count();
    if (!missing.isEmpty()) {
      return (missing.size() == 1 ? "A Peer is" : missing.size() + " Peers are")
          + " not in Consul's WAN pool: "
          + String.join(", ", missing)
          + ". Is that Workstation on, and is its address in YARD_PEERS right?";
    }
    if (notAlive > 0) {
      return notAlive
          + " Workstation"
          + (notAlive == 1 ? " in Consul's WAN pool is" : "s in Consul's WAN pool are")
          + " not alive.";
    }
    return "Consul has a leader, and "
        + members.size()
        + " Workstation"
        + (members.size() == 1 ? " is" : "s are")
        + " in its WAN pool.";
  }

  /** Whether {@code peer}, {@code <host>} or {@code <host>:<port>}, is one of {@code members}. */
  private static boolean inPool(String peer, List<WanMember> members) {
    int colon = peer.lastIndexOf(':');
    boolean hasPort = colon > 0 && peer.indexOf(':') == colon;
    String host = hasPort ? peer.substring(0, colon) : peer;
    int port;
    try {
      port = hasPort ? Integer.parseInt(peer.substring(colon + 1)) : SERF_WAN_PORT;
    } catch (NumberFormatException e) {
      return false;
    }
    List<String> addresses;
    try {
      addresses =
          Arrays.stream(InetAddress.getAllByName(host)).map(InetAddress::getHostAddress).toList();
    } catch (UnknownHostException e) {
      return false;
    }
    return members.stream()
        .anyMatch(member -> member.port() == port && addresses.contains(member.address()));
  }
}
