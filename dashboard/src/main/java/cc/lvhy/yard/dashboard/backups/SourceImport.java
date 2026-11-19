package cc.lvhy.yard.dashboard.backups;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.backups.Backup.Alias;
import cc.lvhy.yard.dashboard.backups.Backup.ContainerBinding;
import cc.lvhy.yard.dashboard.backups.Backup.Service;
import cc.lvhy.yard.dashboard.backups.Backup.StaticInstance;
import cc.lvhy.yard.dashboard.backups.ImportPlan.Step;
import cc.lvhy.yard.dashboard.backups.PlanEntry.Action;
import cc.lvhy.yard.dashboard.backups.PlanEntry.Kind;
import cc.lvhy.yard.dashboard.services.Aliases;
import cc.lvhy.yard.dashboard.services.ContainerBindingRequest;
import cc.lvhy.yard.dashboard.services.ContainerBindingRules;
import cc.lvhy.yard.dashboard.services.ContainerBindings;
import cc.lvhy.yard.dashboard.services.Instance;
import cc.lvhy.yard.dashboard.services.InstancePriorityRequest;
import cc.lvhy.yard.dashboard.services.InvalidRegistrationException;
import cc.lvhy.yard.dashboard.services.Registrations;
import cc.lvhy.yard.dashboard.services.Services;
import cc.lvhy.yard.dashboard.services.StaticInstanceRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Plans the Instance Sources of each Service of a Backup: its Static Instances, Container Bindings
 * and Aliases, and their Instance Priority.
 */
@Component
class SourceImport {

  private final Workstation workstation;

  private final Registrations registrations;

  private final ContainerBindings containerBindings;

  private final Aliases aliases;

  private final Services services;

  SourceImport(
      Workstation workstation,
      Registrations registrations,
      ContainerBindings containerBindings,
      Aliases aliases,
      Services services) {
    this.workstation = workstation;
    this.registrations = registrations;
    this.containerBindings = containerBindings;
    this.aliases = aliases;
    this.services = services;
  }

  /** The host of a Static Instance that names none. */
  String lanAddress() {
    return registrations.defaultHost();
  }

  void plan(PlanDraft draft, String name, Service service, Service existing, String place) {
    Set<String> addresses = new HashSet<>();
    for (int index = 0; index < service.sources().size(); index++) {
      String at = place + ".sources[" + index + "]";
      switch (service.sources().get(index)) {
        case StaticInstance instance ->
            staticInstance(draft, name, instance, existing, addresses, at + ".static-instance");
        case ContainerBinding binding ->
            containerBinding(draft, name, binding, existing, at + ".container-binding");
        case Alias alias -> alias(draft, name, alias, existing, at + ".alias");
      }
    }
  }

  /**
   * Sets the order of the file's sources and its toggle. Sources that the file does not name, such
   * as Foreign Instances, follow them.
   */
  void instancePriority(PlanDraft draft, String service, Service wanted, Service existing) {
    // With one source, there is no order to set. A new Service has the toggle on already.
    if (wanted.sources().size() < 2
        && (existing == null
            ? !Boolean.FALSE.equals(wanted.instancePriority())
            : wanted.instancePriority() == null)) {
      return;
    }
    boolean same =
        existing != null
            && keys(draft, existing).equals(keys(draft, wanted))
            && (wanted.instancePriority() == null
                || wanted.instancePriority().equals(existing.instancePriority()));
    PlanEntry entry = draft.entry(Kind.INSTANCE_PRIORITY, service, null, existing != null, same);
    draft.entries.add(entry);
    if (entry.action() == Action.SKIP) {
      return;
    }
    draft.prioritySteps.add(
        new Step(
            List.of(entry),
            () ->
                services.setInstancePriority(
                    workstation.name(),
                    service,
                    new InstancePriorityRequest(
                        ids(draft, service, wanted), wanted.instancePriority()))));
  }

  private List<String> keys(PlanDraft draft, Service service) {
    return service.sources().stream().map(source -> key(draft, source)).toList();
  }

  /** What matches a source of the file with one of this Workstation. */
  private String key(PlanDraft draft, Backup.Source source) {
    return switch (source) {
      case StaticInstance instance ->
          "static:" + Instance.address(hostOf(draft, instance), instance.port());
      case ContainerBinding binding -> "bound:" + binding.id();
      case Alias alias -> "alias:" + alias.id();
    };
  }

  /** The Instance Source IDs of the file's sources, once the earlier steps wrote them. */
  private List<String> ids(PlanDraft draft, String service, Service wanted) {
    return wanted.sources().stream()
        .map(source ->
            switch (source) {
              case StaticInstance instance ->
                  registrations
                      .staticInstanceAt(service, hostOf(draft, instance), instance.port())
                      .orElseThrow();
              case ContainerBinding binding -> binding.id();
              case Alias alias -> alias.id();
            })
        .toList();
  }

  private void staticInstance(
      PlanDraft draft,
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
      draft.problem(place + staticInstanceField(e.field()), e.getMessage());
      return;
    }
    String address = Instance.address(checked.host(), checked.port());
    if (!addresses.add(address)) {
      draft.problem(
          place,
          "Another Static Instance of " + service + " in the file is at " + address + " too.");
      return;
    }
    StaticInstance match =
        existing == null
            ? null
            : existing.staticInstances().stream()
                .filter(candidate ->
                    hostOf(draft, candidate).equals(checked.host())
                        && candidate.port() == checked.port())
                .findFirst()
                .orElse(null);
    boolean same =
        match != null
            && Objects.equals(match.check(), checked.check())
            && match.tags().equals(checked.tags())
            && match.meta().equals(checked.meta());
    PlanEntry entry = draft.entry(Kind.STATIC_INSTANCE, address, service, match != null, same);
    draft.entries.add(entry);
    if (entry.action() == Action.SKIP) {
      return;
    }
    // The same host and port keep the Instance's ID, so this also overwrites a match.
    draft.instanceSteps.add(
        new Step(
            List.of(entry), () -> registrations.register(workstation.name(), service, checked)));
  }

  private String hostOf(PlanDraft draft, StaticInstance instance) {
    return instance.host() == null ? draft.lanAddress : instance.host();
  }

  private void containerBinding(
      PlanDraft draft, String service, ContainerBinding binding, Service existing, String place) {
    ContainerBindingRequest checked;
    try {
      checked =
          ContainerBindingRules.check(
              service,
              binding.followsCompose()
                  ? ContainerBindingRequest.compose(
                      binding.composeProject(), binding.composeService(), binding.port())
                  : ContainerBindingRequest.container(binding.container(), binding.port()));
    } catch (InvalidRegistrationException e) {
      draft.problem(place + containerBindingField(e.field()), e.getMessage());
      return;
    }
    ContainerBinding normal =
        new ContainerBinding(
            checked.container(),
            checked.composeProject(),
            checked.composeService(),
            checked.port());
    if (existing != null
        && existing.containerBindings().stream()
            .anyMatch(candidate -> candidate.id().equals(normal.id()))) {
      draft.entries.add(
          PlanEntry.skip(Kind.CONTAINER_BINDING, normal.id(), service, PlanDraft.SAME));
      return;
    }
    PlanEntry entry = PlanEntry.add(Kind.CONTAINER_BINDING, normal.id(), service);
    draft.entries.add(entry);
    draft.instanceSteps.add(
        new Step(
            List.of(entry), () -> containerBindings.add(workstation.name(), service, checked)));
  }

  /** An Alias of the file is restored even while its Remote Service has no Yard Scope. */
  private void alias(PlanDraft draft, String service, Alias alias, Service existing, String place) {
    try {
      Aliases.checkTarget("workstation", "Workstation", alias.workstation());
      Aliases.checkTarget("service", "Service", alias.service());
    } catch (InvalidRegistrationException e) {
      draft.problem(place + "." + e.field(), e.getMessage());
      return;
    }
    if (workstation.is(alias.workstation())) {
      draft.problem(
          place + ".workstation",
          "An Alias is only for a Remote Service, and "
              + alias.workstation()
              + " is this Workstation.");
      return;
    }
    boolean there = existing != null && existing.aliases().contains(alias);
    PlanEntry entry = draft.entry(Kind.ALIAS, alias.id(), service, there, there);
    draft.entries.add(entry);
    if (entry.action() != Action.SKIP) {
      draft.aliasSteps.add(
          new Step(
              List.of(entry),
              () -> aliases.restore(service, alias.workstation(), alias.service())));
    }
  }

  /** The place in a Static Instance of the field that {@link Registrations} names. */
  private static String staticInstanceField(String field) {
    return switch (field) {
      case "host", "port", "tags", "meta", "check" -> "." + field;
      case "path", "interval", "timeout" -> ".check." + field;
      default -> "";
    };
  }

  /** The place in a Container Binding of the field that {@link ContainerBindingRules} names. */
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
