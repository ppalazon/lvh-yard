package dev.palazon.lvhyard.dashboard.backups;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.backups.Backup.Alias;
import dev.palazon.lvhyard.dashboard.backups.Backup.ContainerBinding;
import dev.palazon.lvhyard.dashboard.backups.Backup.ExportedFrom;
import dev.palazon.lvhyard.dashboard.backups.Backup.Published;
import dev.palazon.lvhyard.dashboard.backups.Backup.Service;
import dev.palazon.lvhyard.dashboard.backups.Backup.StaticInstance;
import dev.palazon.lvhyard.dashboard.backups.Backup.Unpublished;
import dev.palazon.lvhyard.dashboard.profiles.RouteProfiles;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.Registrations;
import dev.palazon.lvhyard.dashboard.services.SourceKind;
import dev.palazon.lvhyard.dashboard.services.YardReader;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The Backup of what was set up on this Workstation. Bound Instances and Health come from what runs
 * now, and Remote Services belong to other Workstations, so it leaves them out.
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
    yard.services(workstation.name())
        .forEach(service -> services.put(service.name(), service(service, lanAddress)));
    return new Backup(
        new ExportedFrom(workstation.name(), OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)),
        profiles,
        services);
  }

  private static Service service(
      dev.palazon.lvhyard.dashboard.services.Service service, String lanAddress) {
    return new Service(
        service.published()
            ? new Published(
                service.publication().scope().value(),
                service.profiles(),
                service.publication().accessLog())
            : new Unpublished(),
        service.instances().stream()
            .filter(Instance::staticInstance)
            .sorted(Comparator.comparing(Instance::host).thenComparing(Instance::port))
            .map(instance ->
                new StaticInstance(
                    instance.host().equals(lanAddress) ? null : instance.host(),
                    instance.port(),
                    instance.check(),
                    instance.tags(),
                    instance.meta()))
            .toList(),
        service.containerBindings().stream()
            .map(binding ->
                new ContainerBinding(
                    binding.container(),
                    binding.composeProject(),
                    binding.composeService(),
                    binding.port()))
            .toList(),
        service.sources().stream()
            .filter(source -> source.kind() == SourceKind.ALIAS)
            .map(source -> {
              String[] target = source.id().split("/", 2);
              return new Alias(target[0], target.length < 2 ? "" : target[1]);
            })
            .toList());
  }
}
