package cc.lvhy.yard.dashboard.status;

import cc.lvhy.yard.dashboard.consul.Consul;
import cc.lvhy.yard.dashboard.consul.Consul.WanMember;
import cc.lvhy.yard.dashboard.consul.ConsulException;
import cc.lvhy.yard.dashboard.consul.ConsulSettings;
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

  /**
   * How long asking each Workstation's datacenter for its leader may take. A computer that is
   * switched off never answers, so the status must not wait for it as long as Consul does.
   */
  static final Duration READ_TIMEOUT = Duration.ofSeconds(1);

  @FunctionalInterface
  interface Lookup {
    InetAddress[] addresses(String host) throws UnknownHostException;
  }

  private final Consul consul;

  private final ConsulSettings settings;

  private final Lookup lookup;

  /** Lookups and reads run on their own threads, so one that never answers is left behind. */
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
    } catch (ConsulException e) {
      return Part.failing(
          NAME, "Consul does not answer at " + settings.url() + ": " + e.getMessage());
    }
    Map<String, CompletableFuture<Optional<String>>> reads = startReads(members);
    List<Part.Detail> details = new ArrayList<>();
    if (!leader.isEmpty()) {
      details.add(Part.Detail.fact("leader", leader));
    }
    Map<String, String> unread = awaitReads(reads);
    members.stream()
        .sorted(Comparator.comparing(WanMember::datacenter))
        .forEach(member -> {
          String where = member.status() + " at " + member.address() + ":" + member.port();
          String why = unread.get(member.datacenter());
          details.add(
              why != null
                  ? new Part.Detail(member.datacenter(), where + ", but " + why, PartState.WARNING)
                  : new Part.Detail(
                      member.datacenter(),
                      where,
                      member.alive() ? PartState.OK : PartState.WARNING));
        });
    Map<String, String> missing = missingPeers(members);
    missing.forEach(
        (peer, why) -> details.add(new Part.Detail("peer " + peer, why, PartState.WARNING)));
    if (leader.isEmpty()) {
      return new Part(
          NAME, PartState.FAILING, "Consul has no leader, so it cannot accept changes.", details);
    }
    return Part.of(
        NAME,
        reason(members, List.copyOf(missing.keySet()), List.copyOf(unread.keySet())),
        details);
  }

  private static String reason(List<WanMember> members, List<String> missing, List<String> unread) {
    long notAlive = members.stream().filter(member -> !member.alive()).count();
    if (!missing.isEmpty()) {
      return (missing.size() == 1 ? "A Peer is" : missing.size() + " Peers are")
          + " not in Consul's WAN pool: "
          + String.join(", ", missing)
          + ". Make sure that the Workstation is on and that its address in YARD_PEERS is right.";
    }
    if (!unread.isEmpty()) {
      return "Consul cannot read from "
          + String.join(", ", unread)
          + ", though its WAN pool lists "
          + (unread.size() == 1 ? "it" : "them")
          + " as alive.";
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

  /**
   * Asks the datacenter of each alive member for its leader, side by side. A member that Serf
   * counts as alive can still have no path for reads: Serf finds a failure late, and gossip and
   * reads use different ports.
   */
  private Map<String, CompletableFuture<Optional<String>>> startReads(List<WanMember> members) {
    Map<String, CompletableFuture<Optional<String>>> reads = new LinkedHashMap<>();
    members.stream()
        .filter(WanMember::alive)
        .map(WanMember::datacenter)
        .distinct()
        .forEach(datacenter ->
            reads.put(
                datacenter, CompletableFuture.supplyAsync(() -> whyUnread(datacenter), lookups)));
    return reads;
  }

  private Optional<String> whyUnread(String datacenter) {
    try {
      return consul.leader(datacenter).isEmpty()
          ? Optional.of("its datacenter has no leader")
          : Optional.empty();
    } catch (ConsulException e) {
      return Optional.of("Consul cannot read from it: " + Failures.message(e));
    }
  }

  /** Datacenter to the reason, for each that did not answer within {@link #READ_TIMEOUT}. */
  private static Map<String, String> awaitReads(
      Map<String, CompletableFuture<Optional<String>>> reads) {
    long deadline = System.nanoTime() + READ_TIMEOUT.toNanos();
    Map<String, String> unread = new LinkedHashMap<>();
    reads.forEach((datacenter, read) -> {
      try {
        read.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)
            .ifPresent(why -> unread.put(datacenter, why));
      } catch (TimeoutException e) {
        read.cancel(true);
        unread.put(
            datacenter, "its Consul took more than " + READ_TIMEOUT.toSeconds() + " s to answer");
      } catch (ExecutionException e) {
        unread.put(datacenter, "it cannot be read: " + Failures.message(e.getCause()));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        unread.put(datacenter, "it was interrupted while it was read");
      }
    });
    return unread;
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
