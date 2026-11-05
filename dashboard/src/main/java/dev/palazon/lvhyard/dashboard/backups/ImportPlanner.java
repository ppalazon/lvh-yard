package dev.palazon.lvhyard.dashboard.backups;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.backups.Backup.Alias;
import dev.palazon.lvhyard.dashboard.backups.Backup.ContainerBinding;
import dev.palazon.lvhyard.dashboard.backups.Backup.Published;
import dev.palazon.lvhyard.dashboard.backups.Backup.Service;
import dev.palazon.lvhyard.dashboard.backups.Backup.StaticInstance;
import dev.palazon.lvhyard.dashboard.backups.Backup.Unpublished;
import dev.palazon.lvhyard.dashboard.backups.ImportPlan.Step;
import dev.palazon.lvhyard.dashboard.backups.PlanEntry.Kind;
import dev.palazon.lvhyard.dashboard.consul.Consul;
import dev.palazon.lvhyard.dashboard.profiles.RouteProfileChecks;
import dev.palazon.lvhyard.dashboard.profiles.RouteProfileDefinition;
import dev.palazon.lvhyard.dashboard.profiles.RouteProfileEditor;
import dev.palazon.lvhyard.dashboard.profiles.RouteProfiles;
import dev.palazon.lvhyard.dashboard.services.Aliases;
import dev.palazon.lvhyard.dashboard.services.ContainerBindingRequest;
import dev.palazon.lvhyard.dashboard.services.ContainerBindings;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.InstanceKind;
import dev.palazon.lvhyard.dashboard.services.InvalidPriorityException;
import dev.palazon.lvhyard.dashboard.services.InvalidRegistrationException;
import dev.palazon.lvhyard.dashboard.services.PublicationRequest;
import dev.palazon.lvhyard.dashboard.services.Publications;
import dev.palazon.lvhyard.dashboard.services.Registrations;
import dev.palazon.lvhyard.dashboard.services.Scope;
import dev.palazon.lvhyard.dashboard.services.StaticInstanceRequest;
import dev.palazon.lvhyard.dashboard.services.UnknownWorkstationException;
import dev.palazon.lvhyard.dashboard.services.UnreachableWorkstationException;
import dev.palazon.lvhyard.dashboard.services.YardReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Checks a whole Backup against the rules of the API and against this Workstation, and plans the
 * writes. It writes nothing. Each problem names its place in the file.
 */
@Component
class ImportPlanner {

  private static final String EXISTS = "It exists already.";

  private static final String SAME = "It is the same already.";

  private final Workstation workstation;

  private final BackupExport export;

  private final Registrations registrations;

  private final ContainerBindings containerBindings;

  private final Publications publications;

  private final Aliases aliases;

  private final YardReader yard;

  private final RouteProfiles routeProfiles;

  private final RouteProfileChecks routeProfileChecks;

  private final RouteProfileEditor routeProfileEditor;

  ImportPlanner(
      Workstation workstation,
      BackupExport export,
      Registrations registrations,
      ContainerBindings containerBindings,
      Publications publications,
      Aliases aliases,
      YardReader yard,
      RouteProfiles routeProfiles,
      RouteProfileChecks routeProfileChecks,
      RouteProfileEditor routeProfileEditor) {
    this.workstation = workstation;
    this.export = export;
    this.registrations = registrations;
    this.containerBindings = containerBindings;
    this.publications = publications;
    this.aliases = aliases;
    this.yard = yard;
    this.routeProfiles = routeProfiles;
    this.routeProfileChecks = routeProfileChecks;
    this.routeProfileEditor = routeProfileEditor;
  }

  /**
   * @throws RefusedBackupException with every problem of the file; then nothing is planned
   */
  ImportPlan plan(Backup backup, ImportMode mode) {
    return new Run(backup, mode).plan();
  }

  /** One import's plan, as it is built. */
  private final class Run {

    private final Backup backup;

    private final ImportMode mode;

    /** What this Workstation has now, in the same model as the file. */
    private final Backup current = export.backup();

    private final Map<String, RouteProfileDefinition> storedProfiles = routeProfiles.stored(null);

    private final String lanAddress = registrations.defaultHost();

    private final List<BackupProblem> problems = new ArrayList<>();

    private final List<PlanEntry> entries = new ArrayList<>();

    private final List<Step> profileSteps = new ArrayList<>();

    private final List<Step> instanceSteps = new ArrayList<>();

    private final List<Step> publicationSteps = new ArrayList<>();

    private final List<Step> aliasSteps = new ArrayList<>();

    /** The Service of each target that a Binding of the file follows, by target. */
    private final Map<String, String> fileTargets = new HashMap<>();

    Run(Backup backup, ImportMode mode) {
      this.backup = backup;
      this.mode = mode;
    }

    ImportPlan plan() {
      routeProfiles();
      backup.services().forEach(this::service);
      backup.aliases().forEach(this::alias);
      if (!problems.isEmpty()) {
        throw new RefusedBackupException(problems);
      }
      List<Step> steps = new ArrayList<>(profileSteps);
      steps.addAll(instanceSteps);
      steps.addAll(publicationSteps);
      steps.addAll(aliasSteps);
      return new ImportPlan(backup, mode, entries, steps);
    }

    private void routeProfiles() {
      Map<String, Map<String, Object>> added = new LinkedHashMap<>();
      List<PlanEntry> addEntries = new ArrayList<>();
      backup
          .routeProfiles()
          .forEach((name, middleware) -> {
            RouteProfileDefinition stored = storedProfiles.get(name);
            if (stored != null) {
              entries.add(
                  PlanEntry.skip(
                      Kind.ROUTE_PROFILE,
                      name,
                      null,
                      stored.own().equals(middleware) ? SAME : EXISTS));
            } else {
              PlanEntry entry = PlanEntry.add(Kind.ROUTE_PROFILE, name, null);
              entries.add(entry);
              addEntries.add(entry);
              added.put(name, middleware);
            }
          });
      if (added.isEmpty()) {
        return;
      }
      routeProfileChecks
          .check(added, storedProfiles)
          .problems()
          .forEach(problem -> problem("route-profiles." + problem.profile(), problem.reason()));
      if (added.size() > Consul.TRANSACTION_SIZE) {
        problem(
            "route-profiles",
            "The file adds "
                + added.size()
                + " Route Profiles, but one import adds at most "
                + Consul.TRANSACTION_SIZE
                + ".");
      }
      profileSteps.add(new Step(addEntries, () -> routeProfileEditor.save(added)));
    }

    private void service(String name, Service service) {
      String place = "services." + name;
      try {
        Registrations.checkName("Service", name);
      } catch (InvalidRegistrationException e) {
        problem(place, e.getMessage());
        return;
      }
      Service existing = current.services().get(name);
      PlanEntry serviceEntry =
          existing != null
              ? PlanEntry.skip(Kind.SERVICE, name, null, EXISTS)
              : PlanEntry.add(Kind.SERVICE, name, null);
      int at = entries.size();
      entries.add(serviceEntry);
      int steps = instanceSteps.size() + publicationSteps.size();
      Set<String> addresses = new HashSet<>();
      for (int index = 0; index < service.staticInstances().size(); index++) {
        staticInstance(
            name,
            service.staticInstances().get(index),
            existing,
            addresses,
            place + ".static-instances[" + index + "]");
      }
      for (int index = 0; index < service.containerBindings().size(); index++) {
        containerBinding(
            name,
            service.containerBindings().get(index),
            existing,
            place + ".container-bindings[" + index + "]");
      }
      publication(name, service.publication(), existing, place + ".publication");
      if (existing == null && instanceSteps.size() + publicationSteps.size() == steps) {
        entries.set(
            at,
            PlanEntry.skip(
                Kind.SERVICE,
                name,
                null,
                "It has no Static Instance, Container Binding or Publication to add."));
      }
    }

    private void staticInstance(
        String service,
        StaticInstance instance,
        Service existing,
        Set<String> addresses,
        String place) {
      StaticInstanceRequest checked;
      try {
        checked =
            registrations.check(
                service,
                new StaticInstanceRequest(
                    instance.host(),
                    instance.port(),
                    instance.check(),
                    instance.tags(),
                    instance.meta(),
                    false));
      } catch (InvalidRegistrationException e) {
        problem(place + staticInstanceField(e.field()), e.getMessage());
        return;
      }
      String address = Instance.address(checked.host(), checked.port());
      if (!addresses.add(address)) {
        problem(
            place,
            "Another Static Instance of " + service + " in the file is at " + address + " too.");
        return;
      }
      StaticInstance match =
          existing == null
              ? null
              : existing.staticInstances().stream()
                  .filter(candidate ->
                      hostOf(candidate).equals(checked.host())
                          && candidate.port() == checked.port())
                  .findFirst()
                  .orElse(null);
      if (match != null) {
        entries.add(
            PlanEntry.skip(
                Kind.STATIC_INSTANCE,
                address,
                service,
                Objects.equals(match.check(), checked.check())
                        && match.tags().equals(checked.tags())
                        && match.meta().equals(checked.meta())
                    ? SAME
                    : EXISTS));
        return;
      }
      PlanEntry entry = PlanEntry.add(Kind.STATIC_INSTANCE, address, service);
      entries.add(entry);
      instanceSteps.add(
          new Step(
              List.of(entry), () -> registrations.register(workstation.name(), service, checked)));
    }

    private String hostOf(StaticInstance instance) {
      return instance.host() == null ? lanAddress : instance.host();
    }

    private void containerBinding(
        String service, ContainerBinding binding, Service existing, String place) {
      ContainerBindingRequest checked;
      try {
        checked =
            ContainerBindings.check(
                service,
                binding.followsCompose()
                    ? ContainerBindingRequest.compose(
                        binding.composeProject(), binding.composeService(), binding.port())
                    : ContainerBindingRequest.container(binding.container(), binding.port()));
      } catch (InvalidRegistrationException e) {
        problem(place + containerBindingField(e.field()), e.getMessage());
        return;
      }
      ContainerBinding normal =
          new ContainerBinding(
              checked.container(),
              checked.composeProject(),
              checked.composeService(),
              checked.port());
      String target = target(normal);
      String other = fileTargets.putIfAbsent(target, service);
      if (other != null) {
        problem(
            place,
            "The file binds "
                + follows(normal)
                + " twice, but one Binding is the most that a container can have: "
                + (other.equals(service) ? "again for " + service : "for " + other + " too")
                + ".");
        return;
      }
      if (existing != null
          && existing.containerBindings().stream()
              .anyMatch(candidate -> candidate.id().equals(normal.id()))) {
        entries.add(PlanEntry.skip(Kind.CONTAINER_BINDING, normal.id(), service, SAME));
        return;
      }
      current.services().entrySet().stream()
          .filter(byService ->
              byService.getValue().containerBindings().stream()
                  .anyMatch(candidate -> target(candidate).equals(target)))
          .map(Map.Entry::getKey)
          .findFirst()
          .ifPresentOrElse(
              following ->
                  problem(
                      place,
                      "The Service "
                          + following
                          + " follows "
                          + follows(normal)
                          + " already. A container can have one Binding only."),
              () -> {
                PlanEntry entry = PlanEntry.add(Kind.CONTAINER_BINDING, normal.id(), service);
                entries.add(entry);
                instanceSteps.add(
                    new Step(
                        List.of(entry),
                        () -> containerBindings.add(workstation.name(), service, checked)));
              });
    }

    private void publication(
        String service, Backup.Publication publication, Service existing, String place) {
      Backup.Publication now = existing == null ? new Unpublished() : existing.publication();
      switch (publication) {
        case null -> {}
        case Unpublished unpublished ->
            entries.add(
                PlanEntry.skip(
                    Kind.PUBLICATION,
                    service,
                    null,
                    now instanceof Unpublished ? "It is Unpublished already." : EXISTS));
        case Published published -> published(service, published, now, place);
      }
    }

    private void published(
        String service, Published published, Backup.Publication now, String place) {
      Scope scope = Scope.LOCAL;
      if (published.scope() != null) {
        scope = Scope.named(published.scope()).orElse(null);
        if (scope == null) {
          problem(
              place + ".scope",
              "The Scope " + published.scope() + " is not one LVH Yard has. Use local or yard.");
        }
      }
      List<String> priority =
          InstanceKind.DEFAULT_PRIORITY.stream().map(InstanceKind::value).toList();
      if (published.priority() != null) {
        try {
          InstanceKind.priority(published.priority());
          priority = published.priority();
        } catch (InvalidPriorityException e) {
          problem(place + ".priority", e.getMessage());
        }
      }
      for (int index = 0; index < published.routeProfiles().size(); index++) {
        String profile = published.routeProfiles().get(index);
        if (!backup.routeProfiles().containsKey(profile) && !storedProfiles.containsKey(profile)) {
          problem(
              place + ".route-profiles[" + index + "]",
              "The Route Profile "
                  + profile
                  + " is not in the file, and this Workstation does not have it.");
        }
      }
      if (now instanceof Published) {
        Published wanted =
            new Published(
                scope == null ? null : scope.value(), priority, published.routeProfiles());
        entries.add(
            PlanEntry.skip(Kind.PUBLICATION, service, null, now.equals(wanted) ? SAME : EXISTS));
        return;
      }
      PlanEntry entry = PlanEntry.add(Kind.PUBLICATION, service, null);
      entries.add(entry);
      PublicationRequest request =
          new PublicationRequest(scope, priority, published.routeProfiles());
      publicationSteps.add(
          new Step(
              List.of(entry), () -> publications.restore(workstation.name(), service, request)));
    }

    private void alias(String name, Alias alias) {
      String place = "aliases." + name;
      try {
        Registrations.checkName("Alias", name);
      } catch (InvalidRegistrationException e) {
        problem(place, e.getMessage());
        return;
      }
      if (workstation.is(alias.workstation())) {
        problem(
            place + ".workstation",
            "An Alias is only for a Remote Service, and "
                + alias.workstation()
                + " is this Workstation.");
        return;
      }
      Alias now = current.aliases().get(name);
      if (now != null) {
        entries.add(PlanEntry.skip(Kind.ALIAS, name, null, now.equals(alias) ? SAME : EXISTS));
        return;
      }
      try {
        if (yard.service(alias.workstation(), alias.service()).isEmpty()) {
          problem(
              place + ".service",
              "Workstation " + alias.workstation() + " has no Service " + alias.service() + ".");
          return;
        }
      } catch (UnknownWorkstationException | UnreachableWorkstationException e) {
        problem(place + ".workstation", e.getMessage());
        return;
      }
      PlanEntry entry = PlanEntry.add(Kind.ALIAS, name, null);
      entries.add(entry);
      aliasSteps.add(
          new Step(List.of(entry), () -> aliases.add(name, alias.workstation(), alias.service())));
    }

    private void problem(String place, String reason) {
      problems.add(new BackupProblem(place, reason));
    }
  }

  /** What two Bindings follow when no two Services may follow it at once. */
  private static String target(ContainerBinding binding) {
    return binding.followsCompose()
        ? "compose:" + binding.composeProject() + ":" + binding.composeService()
        : binding.container();
  }

  private static String follows(ContainerBinding binding) {
    return binding.followsCompose()
        ? "the Compose service "
            + binding.composeService()
            + " of project "
            + binding.composeProject()
        : "the container " + binding.container();
  }

  /** The place in a Static Instance of the field that {@link Registrations} names. */
  private static String staticInstanceField(String field) {
    return switch (field) {
      case "host", "port", "tags", "meta", "check" -> "." + field;
      case "path", "interval", "timeout" -> ".check." + field;
      default -> "";
    };
  }

  /** The place in a Container Binding of the field that {@link ContainerBindings} names. */
  private static String containerBindingField(String field) {
    return switch (field) {
      case "container" -> ".container";
      case "composeProject" -> ".compose.project";
      case "composeService" -> ".compose.service";
      case "containerPort" -> ".port";
      default -> "";
    };
  }
}
