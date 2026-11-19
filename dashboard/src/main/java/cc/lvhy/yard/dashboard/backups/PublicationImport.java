package cc.lvhy.yard.dashboard.backups;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.backups.Backup.Published;
import cc.lvhy.yard.dashboard.backups.Backup.Service;
import cc.lvhy.yard.dashboard.backups.Backup.Unpublished;
import cc.lvhy.yard.dashboard.backups.ImportPlan.Step;
import cc.lvhy.yard.dashboard.backups.PlanEntry.Action;
import cc.lvhy.yard.dashboard.backups.PlanEntry.Kind;
import cc.lvhy.yard.dashboard.services.PublicationRequest;
import cc.lvhy.yard.dashboard.services.Publications;
import cc.lvhy.yard.dashboard.services.Scope;
import java.util.List;
import org.springframework.stereotype.Component;

/** Plans the Publication of each Service of a Backup. */
@Component
class PublicationImport {

  private final Workstation workstation;

  private final Publications publications;

  PublicationImport(Workstation workstation, Publications publications) {
    this.workstation = workstation;
    this.publications = publications;
  }

  void plan(
      PlanDraft draft,
      String service,
      Backup.Publication publication,
      Service existing,
      String place) {
    Backup.Publication now = existing == null ? new Unpublished() : existing.publication();
    switch (publication) {
      case null -> {}
      case Unpublished unpublished -> unpublished(draft, service, now);
      case Published published -> published(draft, service, published, now, place);
    }
  }

  private void unpublished(PlanDraft draft, String service, Backup.Publication now) {
    if (now instanceof Unpublished) {
      draft.entries.add(
          PlanEntry.skip(Kind.PUBLICATION, service, null, "It is Unpublished already."));
      return;
    }
    PlanEntry entry = draft.entry(Kind.PUBLICATION, service, null, true, false);
    draft.entries.add(entry);
    if (entry.action() == Action.UPDATE) {
      draft.publicationSteps.add(
          new Step(List.of(entry), () -> publications.unpublish(workstation.name(), service)));
    }
  }

  private void published(
      PlanDraft draft, String service, Published published, Backup.Publication now, String place) {
    Scope scope = Scope.LOCAL;
    if (published.scope() != null) {
      scope = Scope.named(published.scope()).orElse(null);
      if (scope == null) {
        draft.problem(
            place + ".scope",
            "The Scope " + published.scope() + " is not one LVH Yard has. Use local or yard.");
      }
    }
    for (int index = 0; index < published.routeProfiles().size(); index++) {
      String profile = published.routeProfiles().get(index);
      if (!draft.backup.routeProfiles().containsKey(profile)
          && !draft.storedProfiles.containsKey(profile)) {
        draft.problem(
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
            published.accessLog(),
            published.mutualTls());
    PlanEntry entry =
        draft.entry(Kind.PUBLICATION, service, null, now instanceof Published, now.equals(wanted));
    draft.entries.add(entry);
    if (entry.action() == Action.SKIP) {
      return;
    }
    // Every part is set, so an overwrite keeps nothing of the Publication it replaces.
    PublicationRequest request =
        new PublicationRequest(
            scope, published.routeProfiles(), published.accessLog(), published.mutualTls());
    draft.publicationSteps.add(
        new Step(List.of(entry), () -> publications.publish(workstation.name(), service, request)));
  }
}
