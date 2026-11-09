package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.consul.Consul;
import dev.palazon.lvhyard.dashboard.consul.Consul.AgentService;
import dev.palazon.lvhyard.dashboard.consul.Consul.HealthEntry;
import dev.palazon.lvhyard.dashboard.consul.ConsulSettings;
import dev.palazon.lvhyard.dashboard.profiles.RouteProfileDefinition;
import dev.palazon.lvhyard.dashboard.profiles.RouteProfiles;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Gives each published Service its hostname: the one rule for hostnames, which the Routes and the
 * pages both follow. A datacenter that does not answer makes its Workstation unreachable, and the
 * others still load. Remote Services with Local Scope get no hostname here (ADR-0003).
 */
@Component
public class YardReader {

  private static final Logger LOG = LoggerFactory.getLogger(YardReader.class);

  private static final Comparator<Instance> BY_ADDRESS =
      Comparator.comparing(Instance::host).thenComparingInt(Instance::port);

  /**
   * One number and unit of a Go duration, such as the {@code 1m} and the {@code 30s} of {@code
   * 1m30s}.
   */
  private static final Pattern GO_DURATION_PART =
      Pattern.compile("(\\d+(?:\\.\\d+)?)(h|ms|m|s|us|µs|ns)");

  private final Workstation workstation;

  private final Consul consul;

  private final ServiceRecords records;

  private final Publications publications;

  private final ContainerBindings containerBindings;

  private final OutOfRange outOfRange;

  private final RouteProfiles routeProfiles;

  /**
   * When another Workstation is not asked: after two reads in a row found it unreachable, for the
   * unreachable pause. Then one read tries it again, and the Workstation shows again by itself once
   * that read succeeds. So a Workstation that is switched off does not make every load wait for
   * Consul's timeout.
   */
  private final CircuitBreakerRegistry breakers;

  YardReader(
      Workstation workstation,
      Consul consul,
      ServiceRecords records,
      Publications publications,
      ContainerBindings containerBindings,
      OutOfRange outOfRange,
      RouteProfiles routeProfiles,
      ConsulSettings consulSettings) {
    this.workstation = workstation;
    this.consul = consul;
    this.records = records;
    this.publications = publications;
    this.containerBindings = containerBindings;
    this.outOfRange = outOfRange;
    this.routeProfiles = routeProfiles;
    this.breakers =
        CircuitBreakerRegistry.of(
            CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(100)
                .waitDurationInOpenState(consulSettings.unreachablePause())
                .permittedNumberOfCallsInHalfOpenState(1)
                // Any other error means the datacenter answered: a fault, not an unreachable
                // Workstation.
                .recordExceptions(ResourceAccessException.class, HttpServerErrorException.class)
                .build());
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
          consul.datacenters().stream()
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

  /** While you created it, or an Instance gives it. */
  public Optional<Service> service(String workstation, String name) {
    checkInYard(workstation);
    Optional<Service> service =
        reading(
            workstation,
            datacenter ->
                service(
                    workstation,
                    datacenter,
                    name,
                    records.all(datacenter).get(name),
                    publications.all(datacenter).get(name),
                    containerBindings.all(datacenter).getOrDefault(name, Map.of()),
                    routeProfiles.stored(datacenter)));
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

  private YardWorkstation remote(String workstation) {
    try {
      return new YardWorkstation(workstation, false, read(workstation));
    } catch (UnreachableWorkstationException e) {
      return new YardWorkstation(workstation, true, List.of());
    }
  }

  private void checkInYard(String workstation) {
    if (!this.workstation.is(workstation) && !consul.datacenters().contains(workstation)) {
      throw new UnknownWorkstationException(workstation);
    }
  }

  private List<Service> read(String workstation) {
    return reading(
        workstation,
        datacenter -> {
          Map<String, ServiceRecords.ServiceRecord> created = records.all(datacenter);
          Map<String, Publication> published = publications.all(datacenter);
          Map<String, Map<String, ContainerBindingRequest>> bindings =
              containerBindings.all(datacenter);
          Map<String, RouteProfileDefinition> profiles = routeProfiles.stored(datacenter);
          TreeSet<String> names = new TreeSet<>(consul.serviceNames(datacenter));
          names.addAll(created.keySet());
          return names.stream()
              .flatMap(name ->
                  service(
                      workstation,
                      datacenter,
                      name,
                      created.get(name),
                      published.get(name),
                      bindings.getOrDefault(name, Map.of()),
                      profiles)
                      .stream())
              .toList();
        });
  }

  /**
   * What {@code read} gives for {@code workstation}'s datacenter: {@code null}, the agent's own,
   * for this Workstation. Another Workstation's datacenter that does not answer in time, or that
   * Consul has no path to (a 5xx), makes it unreachable, and so does a Workstation that is not
   * asked for now ({@link #UNREACHABLE}).
   */
  private <T> T reading(String workstation, Function<String, T> read) {
    if (this.workstation.is(workstation)) {
      return read.apply(null);
    }
    try {
      return breakers.circuitBreaker(workstation).executeSupplier(() -> read.apply(workstation));
    } catch (CallNotPermittedException | ResourceAccessException | HttpServerErrorException e) {
      throw new UnreachableWorkstationException(workstation, e);
    }
  }

  private Optional<Service> service(
      String home,
      String datacenter,
      String name,
      ServiceRecords.ServiceRecord record,
      Publication publication,
      Map<String, ContainerBindingRequest> bindings,
      Map<String, RouteProfileDefinition> profiles) {
    List<Instance> instances =
        consul.health(datacenter, name).stream()
            .filter(entry -> !Consul.isServerItself(entry.service()))
            .map(entry -> instance(entry, !workstation.is(home)))
            .sorted(BY_ADDRESS)
            .toList();
    if (instances.isEmpty() && record == null) {
      return Optional.empty();
    }
    List<ContainerBinding> containerBindings =
        bindings.entrySet().stream()
            .map(binding ->
                new ContainerBinding(
                    binding.getKey(),
                    binding.getValue(),
                    instances.stream()
                        .filter(instance -> binding.getKey().equals(instance.containerBinding()))
                        .map(Instance::id)
                        .toList()))
            .toList();
    List<InstanceSource> sources = new ArrayList<>();
    instances.stream()
        .filter(instance -> !instance.bound())
        .forEach(instance ->
            sources.add(
                new InstanceSource(
                    instance.id(),
                    instance.foreign() ? SourceKind.FOREIGN : SourceKind.STATIC,
                    List.of(instance))));
    containerBindings.forEach(binding ->
        sources.add(
            new InstanceSource(
                binding.id(),
                SourceKind.BOUND,
                instances.stream()
                    .filter(instance -> binding.id().equals(instance.containerBinding()))
                    .toList())));
    // Only this Workstation reads its own Aliases.
    if (record != null && workstation.is(home)) {
      record
          .aliases()
          .forEach(alias -> sources.add(new InstanceSource(alias, SourceKind.ALIAS, List.of())));
    }
    return Optional.of(
        new Service(
            home,
            name,
            record != null,
            publication,
            hostname(home, name, publication),
            instances,
            containerBindings,
            InstanceSource.ordered(record == null ? List.of() : record.order(), sources),
            record == null || record.instancePriority(),
            record != null && !record.aliases().isEmpty(),
            workstation.is(home),
            profiles));
  }

  private String hostname(String home, String name, Publication publication) {
    if (publication == null) {
      return null;
    }
    if (workstation.is(home)) {
      return workstation.hostname(name);
    }
    return publication.scope() == Scope.YARD ? workstation.hostname(home, name) : null;
  }

  /**
   * The Instance that {@code entry} registers. One of a Remote Service is out of range here when
   * its address is in {@link OutOfRange}.
   */
  private Instance instance(HealthEntry entry, boolean remote) {
    AgentService registration = entry.service();
    List<CheckResult> checks =
        entry.checks().stream()
            .map(check ->
                new CheckResult(
                    check.name(),
                    Health.ofConsulStatus(check.status()),
                    check.output() == null ? "" : check.output()))
            .toList();
    HealthCheck check =
        entry.checks().stream()
            .filter(consulCheck -> registration.id().equals(consulCheck.serviceId()))
            .findFirst()
            .map(consulCheck ->
                new HealthCheck(
                    consulCheck.type(),
                    registration.meta().get(Registrations.HTTP_PATH),
                    HealthCheck.format(goDuration(consulCheck.interval())),
                    HealthCheck.format(goDuration(consulCheck.timeout()))))
            .orElse(null);
    Map<String, String> meta = new TreeMap<>(registration.meta());
    meta.keySet().removeIf(key -> key.startsWith(Registrations.RESERVED_META));
    String binding = registration.meta().get(Registrations.CONTAINER_BINDING);
    boolean unpublished = registration.meta().containsKey(Registrations.UNPUBLISHED_CONTAINER_PORT);
    return new Instance(
        registration.id(),
        registration.address(),
        registration.port(),
        Health.worst(checks.stream().map(CheckResult::health).toList()),
        check,
        registration.tags(),
        meta,
        checks,
        remote && outOfRange.contains(registration.address()),
        binding,
        binding == null ? null : registration.meta().get(Registrations.CONTAINER),
        binding == null
            ? null
            : Integer.valueOf(registration.meta().get(Registrations.CONTAINER_PORT)),
        binding == null || unpublished ? null : registration.port(),
        unpublished,
        binding == null && !Registrations.isStaticInstance(registration.meta()));
  }

  /** A duration as Consul writes it, in Go's way, such as {@code 1m30s} or {@code 1.5s}. */
  private static Duration goDuration(String text) {
    BigDecimal nanos = BigDecimal.ZERO;
    Matcher part = GO_DURATION_PART.matcher(text == null ? "" : text);
    while (part.find()) {
      BigDecimal unit =
          switch (part.group(2)) {
            case "h" -> BigDecimal.valueOf(3_600_000_000_000L);
            case "m" -> BigDecimal.valueOf(60_000_000_000L);
            case "s" -> BigDecimal.valueOf(1_000_000_000L);
            case "ms" -> BigDecimal.valueOf(1_000_000L);
            case "us", "µs" -> BigDecimal.valueOf(1_000L);
            default -> BigDecimal.ONE;
          };
      nanos = nanos.add(new BigDecimal(part.group(1)).multiply(unit));
    }
    return Duration.ofNanos(nanos.longValue());
  }
}
