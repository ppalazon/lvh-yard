package dev.palazon.lvhyard.dashboard.backups;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.backups.Backup.Alias;
import dev.palazon.lvhyard.dashboard.backups.Backup.ContainerBinding;
import dev.palazon.lvhyard.dashboard.backups.Backup.Published;
import dev.palazon.lvhyard.dashboard.backups.Backup.Service;
import dev.palazon.lvhyard.dashboard.backups.Backup.StaticInstance;
import dev.palazon.lvhyard.dashboard.backups.Backup.Unpublished;
import dev.palazon.lvhyard.dashboard.backups.ImportPlan.Step;
import dev.palazon.lvhyard.dashboard.backups.PlanEntry.Action;
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
import dev.palazon.lvhyard.dashboard.services.InvalidRegistrationException;
import dev.palazon.lvhyard.dashboard.services.PublicationRequest;
import dev.palazon.lvhyard.dashboard.services.Publications;
import dev.palazon.lvhyard.dashboard.services.Registrations;
import dev.palazon.lvhyard.dashboard.services.Scope;
import dev.palazon.lvhyard.dashboard.services.Services;
import dev.palazon.lvhyard.dashboard.services.StaticInstanceRequest;
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

  private final Services services;

  private final Aliases aliases;

  private final RouteProfiles routeProfiles;

  private final RouteProfileChecks routeProfileChecks;

  private final RouteProfileEditor routeProfileEditor;

  ImportPlanner(
      Workstation workstation,
      BackupExport export,
      Registrations registrations,
      ContainerBindings containerBindings,
      Publications publications,
      Services services,
      Aliases aliases,
      RouteProfiles routeProfiles,
      RouteProfileChecks routeProfileChecks,
      RouteProfileEditor routeProfileEditor) {
    this.workstation = workstation;
    this.export = export;
    this.registrations = registrations;
    this.containerBindings = containerBindings;
    this.publications = publications;
    this.services = services;
    this.aliases = aliases;
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

    private final List<Step> serviceSteps = new ArrayList<>();

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
      steps.addAll(serviceSteps);
      steps.addAll(instanceSteps);
      steps.addAll(publicationSteps);
      steps.addAll(aliasSteps);
      return new ImportPlan(backup, mode, entries, steps);
    }

    private void routeProfiles() {
      Map<String, Map<String, Object>> saved = new LinkedHashMap<>();
      List<PlanEntry> savedEntries = new ArrayList<>();
      backup
          .routeProfiles()
          .forEach((name, middleware) -> {
            RouteProfileDefinition stored = storedProfiles.get(name);
            PlanEntry entry =
                entry(
                    Kind.ROUTE_PROFILE,
                    name,
                    null,
                    stored != null,
                    stored != null && stored.own().equals(middleware));
            entries.add(entry);
            if (entry.action() != Action.SKIP) {
              savedEntries.add(entry);
              saved.put(name, middleware);
            }
          });
      if (saved.isEmpty()) {
        return;
      }
      routeProfileChecks
          .check(saved, storedProfiles)
          .problems()
          .forEach(problem -> problem("route-profiles." + problem.profile(), problem.reason()));
      if (saved.size() > Consul.TRANSACTION_SIZE) {
        problem(
            "route-profiles",
            "The file adds or changes "
                + saved.size()
                + " Route Profiles, but one import adds or changes at most "
                + Consul.TRANSACTION_SIZE
                + ".");
      }
      profileSteps.add(new Step(savedEntries, () -> routeProfileEditor.save(saved)));
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
      } else if (existing == null) {
        serviceSteps.add(
            new Step(List.of(serviceEntry), () -> services.create(workstation.name(), name)));
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
                    instance.meta()));
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
      boolean same =
          match != null
              && Objects.equals(match.check(), checked.check())
              && match.tags().equals(checked.tags())
              && match.meta().equals(checked.meta());
      PlanEntry entry = entry(Kind.STATIC_INSTANCE, address, service, match != null, same);
      entries.add(entry);
      if (entry.action() == Action.SKIP) {
        return;
      }
      // The same host and port keep the Instance's ID, so this also overwrites a match.
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
        case Unpublished unpublished -> unpublished(service, now);
        case Published published -> published(service, published, now, place);
      }
    }

    private void unpublished(String service, Backup.Publication now) {
      if (now instanceof Unpublished) {
        entries.add(PlanEntry.skip(Kind.PUBLICATION, service, null, "It is Unpublished already."));
        return;
      }
      PlanEntry entry = entry(Kind.PUBLICATION, service, null, true, false);
      entries.add(entry);
      if (entry.action() == Action.UPDATE) {
        publicationSteps.add(
            new Step(List.of(entry), () -> publications.unpublish(workstation.name(), service)));
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
      Published wanted =
          new Published(
              scope == null ? null : scope.value(),
              published.routeProfiles(),
              published.accessLog());
      PlanEntry entry =
          entry(Kind.PUBLICATION, service, null, now instanceof Published, now.equals(wanted));
      entries.add(entry);
      if (entry.action() == Action.SKIP) {
        return;
      }
      // Every part is set, so an overwrite keeps nothing of the Publication it replaces.
      PublicationRequest request =
          new PublicationRequest(scope, published.routeProfiles(), published.accessLog());
      publicationSteps.add(
          new Step(
              List.of(entry), () -> publications.publish(workstation.name(), service, request)));
    }

    private void alias(String name, Alias alias) {
      String place = "aliases." + name;
      try {
        Registrations.checkName("Alias", name);
      } catch (InvalidRegistrationException e) {
        problem(place, e.getMessage());
        return;
      }
      try {
        Aliases.checkTarget("workstation", "Workstation", alias.workstation());
        Aliases.checkTarget("service", "Service", alias.service());
      } catch (InvalidRegistrationException e) {
        problem(place + "." + e.field(), e.getMessage());
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
      PlanEntry entry = entry(Kind.ALIAS, name, null, now != null, alias.equals(now));
      entries.add(entry);
      if (entry.action() != Action.SKIP) {
        aliasSteps.add(
            new Step(
                List.of(entry), () -> aliases.add(name, alias.workstation(), alias.service())));
      }
    }

    /**
     * Add when nothing matches. A match is a skip when it is the same, or in add mode, and else an
     * update.
     */
    private PlanEntry entry(Kind kind, String name, String service, boolean matches, boolean same) {
      if (!matches) {
        return PlanEntry.add(kind, name, service);
      }
      if (same) {
        return PlanEntry.skip(kind, name, service, SAME);
      }
      return mode == ImportMode.UPDATE
          ? PlanEntry.update(kind, name, service)
          : PlanEntry.skip(kind, name, service, EXISTS);
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
