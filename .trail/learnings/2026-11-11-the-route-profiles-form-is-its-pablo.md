---
title: "The Route Profiles form is its own page, and the examples beside it are for reference only"
author: Pablo
date: 2026-11-11
branch: go-cli
id: l-81f5e6fa
scope: repo
status: active
replaces: l-2a223619
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/RouteProfilesPagesController.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/RouteProfileEditorPage.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/RouteProfileExample.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/RouteProfileYaml.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/ServicePage.java
---

## Decided
- The New profile button goes to /route-profiles/new (RouteProfileEditorPage.NEW_PATH), an empty form. Change in a row goes to /route-profiles/{profile}/edit, which shows the stored definition as block YAML (RouteProfileYaml.write).
- The RouteProfileExample entries are reference only, not editor presets. The form page shows each one in a details block beside the form, with a link to the Traefik docs. Nothing fills the form from them.
- The form always posts to /route-profiles. A save with a name in the list replaces that profile, so a rename is a save under the new name, then Remove.
- A refused save answers 400 with the page, the reasons in one alert above the form, and the typed name and text. The reasons come from RouteProfileChecks, and a reason can be about the name or the middleware, so they are not put at one field.
- A refused remove answers 409 with the page and the RouteProfileInUseException message in an alert.
- The Service page links each profile name to /route-profiles#profile-<name> only for its own Services. A Remote Service's profiles are on its Home Workstation's dashboard.
