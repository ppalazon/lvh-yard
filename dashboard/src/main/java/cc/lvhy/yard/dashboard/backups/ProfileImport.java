package cc.lvhy.yard.dashboard.backups;

import cc.lvhy.yard.dashboard.backups.ImportPlan.Step;
import cc.lvhy.yard.dashboard.backups.PlanEntry.Action;
import cc.lvhy.yard.dashboard.backups.PlanEntry.Kind;
import cc.lvhy.yard.dashboard.consul.Consul;
import cc.lvhy.yard.dashboard.profiles.RouteProfileChecks;
import cc.lvhy.yard.dashboard.profiles.RouteProfileDefinition;
import cc.lvhy.yard.dashboard.profiles.RouteProfileEditor;
import cc.lvhy.yard.dashboard.profiles.RouteProfiles;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Plans the Route Profiles of a Backup. They are saved in one write, before everything else. */
@Component
class ProfileImport {

  private final RouteProfiles routeProfiles;

  private final RouteProfileChecks routeProfileChecks;

  private final RouteProfileEditor routeProfileEditor;

  ProfileImport(
      RouteProfiles routeProfiles,
      RouteProfileChecks routeProfileChecks,
      RouteProfileEditor routeProfileEditor) {
    this.routeProfiles = routeProfiles;
    this.routeProfileChecks = routeProfileChecks;
    this.routeProfileEditor = routeProfileEditor;
  }

  /** The Route Profiles that this Workstation has now. */
  Map<String, RouteProfileDefinition> stored() {
    return routeProfiles.stored(null);
  }

  void plan(PlanDraft draft) {
    Map<String, Map<String, Object>> saved = new LinkedHashMap<>();
    List<PlanEntry> savedEntries = new ArrayList<>();
    draft
        .backup
        .routeProfiles()
        .forEach((name, middleware) -> {
          RouteProfileDefinition stored = draft.storedProfiles.get(name);
          PlanEntry entry =
              draft.entry(
                  Kind.ROUTE_PROFILE,
                  name,
                  null,
                  stored != null,
                  stored != null && stored.own().equals(middleware));
          draft.entries.add(entry);
          if (entry.action() != Action.SKIP) {
            savedEntries.add(entry);
            saved.put(name, middleware);
          }
        });
    if (saved.isEmpty()) {
      return;
    }
    routeProfileChecks
        .check(saved, draft.storedProfiles)
        .problems()
        .forEach(problem -> draft.problem("route-profiles." + problem.profile(), problem.reason()));
    if (saved.size() > Consul.TRANSACTION_SIZE) {
      draft.problem(
          "route-profiles",
          "The file adds or changes "
              + saved.size()
              + " Route Profiles, but one import adds or changes at most "
              + Consul.TRANSACTION_SIZE
              + ".");
    }
    draft.profileSteps.add(new Step(savedEntries, () -> routeProfileEditor.save(saved)));
  }
}
