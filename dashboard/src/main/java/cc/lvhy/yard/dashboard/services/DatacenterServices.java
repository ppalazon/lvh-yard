package cc.lvhy.yard.dashboard.services;

import cc.lvhy.yard.dashboard.OutOfRange;
import cc.lvhy.yard.dashboard.TraefikSettings;
import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.consul.Consul;
import cc.lvhy.yard.dashboard.consul.ConsulCatalog;
import cc.lvhy.yard.dashboard.profiles.RouteProfileDefinition;
import cc.lvhy.yard.dashboard.profiles.RouteProfiles;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * The Services of one datacenter, put together from its catalog, its records, its Publications, its
 * Container Bindings and its Route Profiles. It gives each published Service its hostname: the one
 * rule for hostnames, which the Routes and the pages both follow. Remote Services with Local Scope
 * get no hostname here (ADR-0003).
 */
@Component
class DatacenterServices {

  private static final Comparator<Instance> BY_ADDRESS =
      Comparator.comparing(Instance::host).thenComparingInt(Instance::port);

  private final Workstation workstation;

  private final ConsulCatalog catalog;

  private final ServiceRecords records;

  private final Publications publications;

  private final ContainerBindings containerBindings;

  private final OutOfRange outOfRange;

  private final RouteProfiles routeProfiles;

  private final TraefikSettings traefikSettings;

  DatacenterServices(
      Workstation workstation,
      ConsulCatalog catalog,
      ServiceRecords records,
      Publications publications,
      ContainerBindings containerBindings,
      OutOfRange outOfRange,
      RouteProfiles routeProfiles,
      TraefikSettings traefikSettings) {
    this.workstation = workstation;
    this.catalog = catalog;
    this.records = records;
    this.publications = publications;
    this.containerBindings = containerBindings;
    this.outOfRange = outOfRange;
    this.routeProfiles = routeProfiles;
    this.traefikSettings = traefikSettings;
  }

  /**
   * Every Service that the catalog or the records name.
   *
   * @param home the Workstation that the datacenter belongs to
   * @param datacenter {@code null} for this Workstation's own
   */
  List<Service> all(String home, String datacenter) {
    Map<String, ServiceRecords.ServiceRecord> created = records.all(datacenter);
    Map<String, Publication> published = publications.all(datacenter);
    Map<String, Map<String, ContainerBindingRequest>> bindings = containerBindings.all(datacenter);
    Map<String, RouteProfileDefinition> profiles = routeProfiles.stored(datacenter);
    TreeSet<String> names = new TreeSet<>(catalog.serviceNames(datacenter));
    names.addAll(created.keySet());
    return names.stream()
        .flatMap(name ->
            service(
                home,
                datacenter,
                name,
                created.get(name),
                published.get(name),
                bindings.getOrDefault(name, Map.of()),
                profiles)
                .stream())
        .toList();
  }

  /** While it has a record, or an Instance gives it. */
  Optional<Service> one(String home, String datacenter, String name) {
    return service(
        home,
        datacenter,
        name,
        records.all(datacenter).get(name),
        publications.all(datacenter).get(name),
        containerBindings.all(datacenter).getOrDefault(name, Map.of()),
        routeProfiles.stored(datacenter));
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
        catalog.health(datacenter, name).stream()
            .filter(entry -> !Consul.isServerItself(entry.service()))
            .map(entry -> InstanceMapping.of(entry, !workstation.is(home), outOfRange))
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
            profiles,
            traefikSettings.enabled() && traefikSettings.httpsPort() != null));
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
}
