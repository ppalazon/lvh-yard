package dev.palazon.lvhyard.dashboard.status;

import dev.palazon.lvhyard.dashboard.consul.Consul;
import dev.palazon.lvhyard.dashboard.consul.Consul.WanMember;
import dev.palazon.lvhyard.dashboard.consul.ConsulSettings;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * A Workstation in the WAN pool that is not alive, or a missing Peer, is only a warning, because a
 * teammate's computer may be off.
 */
@Component
@Order(StatusOrder.CONSUL)
class ConsulStatus implements PartReader {

  static final String NAME = "Consul";

  /**
   * How long looking up the addresses of all Peers given by name may take. Past it, each Peer not
   * yet looked up is a warning of its own, and the part still answers within the status's deadline.
   */
  static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(1);

  @FunctionalInterface
  interface Lookup {
    InetAddress[] addresses(String host) throws UnknownHostException;
  }

  private final Consul consul;

  private final ConsulSettings settings;

  private final Lookup lookup;

  /** Lookups run on their own threads, so one that never answers is simply left behind. */
  private final ExecutorService lookups = Executors.newVirtualThreadPerTaskExecutor();

  @Autowired
  ConsulStatus(Consul consul, ConsulSettings settings) {
    this(consul, settings, InetAddress::getAllByName);
  }

  ConsulStatus(Consul consul, ConsulSettings settings, Lookup lookup) {
    this.consul = consul;
    this.settings = settings;
    this.lookup = lookup;
  }

  @PreDestroy
  void close() {
    lookups.shutdownNow();
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
    List<Part.Detail> details = new ArrayList<>();
    if (!leader.isEmpty()) {
      details.add(Part.Detail.fact("leader", leader));
    }
    members.stream()
        .sorted(Comparator.comparing(WanMember::datacenter))
        .forEach(member ->
            details.add(
                new Part.Detail(
                    member.datacenter(),
                    member.status() + " at " + member.address() + ":" + member.port(),
                    member.alive() ? PartState.OK : PartState.WARNING)));
    Map<String, String> missing = missingPeers(members);
    missing.forEach(
        (peer, why) -> details.add(new Part.Detail("peer " + peer, why, PartState.WARNING)));
    if (leader.isEmpty()) {
      return new Part(
          NAME, PartState.FAILING, "Consul has no leader, so it cannot accept changes.", details);
    }
    return Part.of(NAME, reason(members, List.copyOf(missing.keySet())), details);
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

  /** The lookups run side by side, all within {@link #LOOKUP_TIMEOUT}. */
  private Map<String, String> missingPeers(List<WanMember> members) {
    Map<String, CompletableFuture<Optional<String>>> checks = new LinkedHashMap<>();
    for (String peer : settings.peerAddresses()) {
      checks.put(peer, CompletableFuture.supplyAsync(() -> whyMissing(peer, members), lookups));
    }
    long deadline = System.nanoTime() + LOOKUP_TIMEOUT.toNanos();
    Map<String, String> missing = new LinkedHashMap<>();
    checks.forEach(
        (peer, check) -> await(check, deadline).ifPresent(why -> missing.put(peer, why)));
    return missing;
  }

  private static Optional<String> await(CompletableFuture<Optional<String>> check, long deadline) {
    try {
      return check.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
    } catch (TimeoutException e) {
      check.cancel(true);
      return Optional.of("its address lookup took more than " + LOOKUP_TIMEOUT.toSeconds() + " s");
    } catch (ExecutionException e) {
      return Optional.of("cannot be checked: " + Failures.message(e.getCause()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.of("interrupted while it was checked");
    }
  }

  private Optional<String> whyMissing(String written, List<WanMember> members) {
    Peer peer;
    try {
      peer = Peer.parse(written);
    } catch (IllegalArgumentException e) {
      return Optional.of("not an address: " + e.getMessage());
    }
    List<InetAddress> addresses;
    try {
      addresses = Arrays.asList(lookup.addresses(peer.host()));
    } catch (UnknownHostException e) {
      return Optional.of("its address cannot be found");
    }
    boolean inPool =
        members.stream()
            .anyMatch(member ->
                member.port() == peer.port()
                    && address(member).map(addresses::contains).orElse(false));
    return inPool ? Optional.empty() : Optional.of("not in the WAN pool");
  }

  /** Consul gives an IP address, so no lookup is needed. */
  private static Optional<InetAddress> address(WanMember member) {
    if (member.address() == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(InetAddress.ofLiteral(member.address()));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
