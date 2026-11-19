package cc.lvhy.yard.dashboard.backups;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.backups.Backup.Service;
import cc.lvhy.yard.dashboard.backups.ImportPlan.Step;
import cc.lvhy.yard.dashboard.backups.PlanEntry.Kind;
import cc.lvhy.yard.dashboard.services.InstanceRules;
import cc.lvhy.yard.dashboard.services.InvalidRegistrationException;
import cc.lvhy.yard.dashboard.services.Services;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Checks a whole Backup against the rules of the API and against this Workstation, and plans the
 * writes. It writes nothing. Each problem names its place in the file. It plans the Services, and
 * leaves the Route Profiles, the Instance Sources and the Publications to {@link ProfileImport},
 * {@link SourceImport} and {@link PublicationImport}.
 */
@Component
class ImportPlanner {

  private final Workstation workstation;

  private final BackupExport export;

  private final Services services;

  private final ProfileImport profiles;

  private final SourceImport sources;

  private final PublicationImport publications;

  ImportPlanner(
      Workstation workstation,
      BackupExport export,
      Services services,
      ProfileImport profiles,
      SourceImport sources,
      PublicationImport publications) {
    this.workstation = workstation;
    this.export = export;
    this.services = services;
    this.profiles = profiles;
    this.sources = sources;
    this.publications = publications;
  }

  /**
   * @throws RefusedBackupException with every problem of the file; then nothing is planned
   */
  ImportPlan plan(Backup backup, ImportMode mode) {
    PlanDraft draft =
        new PlanDraft(backup, mode, export.backup(), profiles.stored(), sources.lanAddress());
    profiles.plan(draft);
    backup.services().forEach((name, service) -> service(draft, name, service));
    return draft.plan();
  }

  private void service(PlanDraft draft, String name, Service service) {
    String place = "services." + name;
    try {
      InstanceRules.checkName("Service", name);
    } catch (InvalidRegistrationException e) {
      draft.problem(place, e.getMessage());
      return;
    }
    Service existing = draft.current.services().get(name);
    if (existing == null) {
      PlanEntry entry = PlanEntry.add(Kind.SERVICE, name, null);
      draft.entries.add(entry);
      draft.serviceSteps.add(
          new Step(List.of(entry), () -> services.restore(workstation.name(), name)));
    } else {
      draft.entries.add(PlanEntry.skip(Kind.SERVICE, name, null, PlanDraft.EXISTS));
    }
    sources.plan(draft, name, service, existing, place);
    publications.plan(draft, name, service.publication(), existing, place + ".publication");
    sources.instancePriority(draft, name, service, existing);
  }
}
