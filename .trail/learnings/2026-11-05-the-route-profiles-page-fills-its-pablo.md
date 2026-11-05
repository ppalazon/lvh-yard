---
title: "The Route Profiles page fills its form from an address, so it needs no script"
author: Pablo
date: 2026-11-05
branch: worktree-agent-a4a4dcb7c4ceb5498
cost: { minutes: 26, tokens: 62340 }
id: l-2a223619
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/RouteProfilesPagesController.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/RouteProfileExample.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/RouteProfileYaml.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/ServicePage.java
---

## Decided
- The New profile button is a GET form with a select: /route-profiles?example=<key> loads the page with that RouteProfileExample in the form. Change in a row goes to /route-profiles/{name}/edit, which shows the stored definition as block YAML (RouteProfileYaml.write). So the page needs no script, as the Service page.
- The form always posts to /route-profiles. A save with a name in the list replaces that profile, so a rename is a save under the new name, then Remove.
- A refused save answers 400 with the page, the reasons in one alert above the form, and the typed name and text. The reasons come from RouteProfileChecks, and a reason can be about the name or the middleware, so they are not put at one field.
- A refused remove answers 409 with the page and the RouteProfileInUseException message in an alert.
- The Service page links each profile name to /route-profiles#profile-<name> only for its own Services. A Remote Service's profiles are on its Home Workstation's dashboard.

## Watch out
- The examples must pass RouteProfileChecks: RouteProfilesPageTest saves each one in order, and the chain example names the basic auth and IP allow list examples. The basic auth hash is fake, but the checks accept it.
