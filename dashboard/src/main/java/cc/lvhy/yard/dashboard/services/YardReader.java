package cc.lvhy.yard.dashboard.services;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.consul.ConsulFederation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The Yard as this Workstation sees it: its own Services and those of each other Workstation. A
 * datacenter that does not answer makes its Workstation unreachable, and the others still load.
 */
@Component
public class YardReader {

  private static final Logger LOG = LoggerFactory.getLogger(YardReader.class);

  private final Workstation workstation;

  private final ConsulFederation federation;

  private final DatacenterReads reads;

  private final DatacenterServices datacenterServices;

  YardReader(
      Workstation workstation,
      ConsulFederation federation,
      DatacenterReads reads,
      DatacenterServices datacenterServices) {
    this.workstation = workstation;
    this.federation = federation;
    this.reads = reads;
    this.datacenterServices = datacenterServices;
  }

  /**
   * The others are read side by side, so a Workstation that does not answer delays the rest only
   * once.
   */
  public Yard yard() {
    return yard(this::remote);
  }

  /** This Workstation's first, then by Workstation and name. */
  public List<Service> services() {
    return yard().services();
  }

  /**
   * Any other Workstation whose Services fail to read, for any reason, is unreachable here, so it
   * never takes the Routes of the others with it. The fault is logged, because the API still
   * reports it.
   */
  public Yard routableYard() {
    return yard(remote -> {
      try {
        return remote(remote);
      } catch (RuntimeException fault) {
        LOG.warn("The Services of {} cannot be read, so it gets no Routes for now", remote, fault);
        return new YardWorkstation(remote, true, List.of());
      }
    });
  }

  private Yard yard(Function<String, YardWorkstation> remote) {
    try (ExecutorService readers = Executors.newVirtualThreadPerTaskExecutor()) {
      List<CompletableFuture<YardWorkstation>> remotes =
          federation.datacenters().stream()
              .filter(datacenter -> !workstation.is(datacenter))
              .sorted()
              .map(name -> CompletableFuture.supplyAsync(() -> remote.apply(name), readers))
              .toList();
      List<Service> own = read(workstation.name());
      List<YardWorkstation> others = remotes.stream().map(CompletableFuture::join).toList();
      HomeWorkstation home =
          new HomeWorkstation(
              workstation.name(),
              withAliases(own, (target, service) -> Yard.remoteService(others, target, service)));
      return new Yard(home, others);
    }
  }

  public List<Service> services(String workstation) {
    checkInYard(workstation);
    List<Service> services = read(workstation);
    return this.workstation.is(workstation) ? withAliases(services, this::aliasTarget) : services;
  }

  /** While it has a record, or an Instance gives it. */
  public Optional<Service> service(String workstation, String name) {
    checkInYard(workstation);
    Optional<Service> service =
        reads.read(
            workstation, datacenter -> datacenterServices.one(workstation, datacenter, name));
    return this.workstation.is(workstation)
        ? service.map(found -> withAliases(List.of(found), this::aliasTarget).getFirst())
        : service;
  }

  /**
   * Gives each Alias source of this Workstation's {@code services} the Instances of its Remote
   * Service, while that one has Yard Scope. Only this Workstation's own Aliases count, so no other
   * Workstation's Route depends on them.
   *
   * @param remoteService finds a Remote Service by its Workstation and name
   */
  private List<Service> withAliases(
      List<Service> services, BiFunction<String, String, Optional<Service>> remoteService) {
    return services.stream()
        .map(service -> {
          Map<String, List<Instance>> aliased = new TreeMap<>();
          service.sources().stream()
              .filter(source -> source.kind() == SourceKind.ALIAS)
              .forEach(alias -> {
                AliasTarget target = AliasTarget.parse(alias.id());
                remoteService
                    .apply(target.workstation(), target.service())
                    .filter(Service::sharedWithYard)
                    .ifPresent(found -> aliased.put(alias.id(), found.instances()));
              });
          return aliased.isEmpty() ? service : service.withAliases(aliased);
        })
        .toList();
  }

  /**
   * Any fault reading it means no Alias fallback, so it never breaks this Workstation's own
   * Services. The read goes through the other Workstation's circuit breaker, so one that is
   * switched off does not slow this Workstation's own answers for long.
   */
  private Optional<Service> aliasTarget(String remote, String service) {
    if (workstation.is(remote)) {
      return Optional.empty();
    }
    try {
      return service(remote, service);
    } catch (RuntimeException fault) {
      LOG.debug("No Alias fallback to {} of {}", service, remote, fault);
      return Optional.empty();
    }
  }

  private List<Service> read(String workstation) {
    return reads.read(workstation, datacenter -> datacenterServices.all(workstation, datacenter));
  }

  private YardWorkstation remote(String workstation) {
    try {
      return new YardWorkstation(workstation, false, read(workstation));
    } catch (UnreachableWorkstationException e) {
      return new YardWorkstation(workstation, true, List.of());
    }
  }

  private void checkInYard(String workstation) {
    if (!this.workstation.is(workstation) && !federation.datacenters().contains(workstation)) {
      throw new UnknownWorkstationException(workstation);
    }
  }
}
