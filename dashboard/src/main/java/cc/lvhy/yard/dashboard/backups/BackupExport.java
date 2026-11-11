package cc.lvhy.yard.dashboard.backups;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.backups.Backup.Alias;
import cc.lvhy.yard.dashboard.backups.Backup.ContainerBinding;
import cc.lvhy.yard.dashboard.backups.Backup.ExportedFrom;
import cc.lvhy.yard.dashboard.backups.Backup.Published;
import cc.lvhy.yard.dashboard.backups.Backup.Service;
import cc.lvhy.yard.dashboard.backups.Backup.Source;
import cc.lvhy.yard.dashboard.backups.Backup.StaticInstance;
import cc.lvhy.yard.dashboard.backups.Backup.Unpublished;
import cc.lvhy.yard.dashboard.profiles.RouteProfiles;
import cc.lvhy.yard.dashboard.services.AliasTarget;
import cc.lvhy.yard.dashboard.services.Instance;
import cc.lvhy.yard.dashboard.services.InstanceSource;
import cc.lvhy.yard.dashboard.services.Registrations;
import cc.lvhy.yard.dashboard.services.YardReader;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The Backup of what was set up on this Workstation. Bound Instances and Health come from what runs
 * now, Remote Services belong to other Workstations, and Foreign Instances belong to other tools,
 * so it leaves them out.
 */
@Component
public class BackupExport {

  private final Workstation workstation;

  private final YardReader yard;

  private final Registrations registrations;

  private final RouteProfiles routeProfiles;

  BackupExport(
      Workstation workstation,
      YardReader yard,
      Registrations registrations,
      RouteProfiles routeProfiles) {
    this.workstation = workstation;
    this.yard = yard;
    this.registrations = registrations;
    this.routeProfiles = routeProfiles;
  }

  public Backup backup() {
    String lanAddress = registrations.defaultHost();
    Map<String, Map<String, Object>> profiles = new LinkedHashMap<>();
    routeProfiles.stored(null).forEach((name, definition) -> profiles.put(name, definition.own()));
    Map<String, Service> services = new LinkedHashMap<>();
    yard.services(workstation.name()).stream()
        // A Service that only Foreign Instances give was not set up with LVH Yard.
        .filter(cc.lvhy.yard.dashboard.services.Service::created)
        .forEach(service -> services.put(service.name(), service(service, lanAddress)));
    return new Backup(
        new ExportedFrom(workstation.name(), OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)),
        profiles,
        services);
  }

  private static Service service(
      cc.lvhy.yard.dashboard.services.Service service, String lanAddress) {
    Map<String, Instance> instances = new HashMap<>();
    service.instances().stream()
        .filter(Instance::staticInstance)
        .forEach(instance -> instances.put(instance.id(), instance));
    Map<String, cc.lvhy.yard.dashboard.services.ContainerBinding> bindings = new HashMap<>();
    service.containerBindings().forEach(binding -> bindings.put(binding.id(), binding));
    List<Source> sources = new ArrayList<>();
    for (InstanceSource source : service.sources()) {
      switch (source.kind()) {
        case STATIC -> {
          Instance instance = instances.get(source.id());
          if (instance != null) {
            sources.add(
                new StaticInstance(
                    instance.host().equals(lanAddress) ? null : instance.host(),
                    instance.port(),
                    instance.check(),
                    instance.tags(),
                    instance.meta()));
          }
        }
        case BOUND -> {
          var binding = bindings.get(source.id());
          if (binding != null) {
            sources.add(
                new ContainerBinding(
                    binding.container(),
                    binding.composeProject(),
                    binding.composeService(),
                    binding.port()));
          }
        }
        case ALIAS -> {
          AliasTarget target = AliasTarget.parse(source.id());
          sources.add(new Alias(target.workstation(), target.service()));
        }
        case FOREIGN -> {}
      }
    }
    return new Service(
        service.published()
            ? new Published(
                service.publication().scope().value(),
                service.profiles(),
                service.publication().accessLog())
            : new Unpublished(),
        service.instancePriority(),
        sources);
  }
}
