---
title: An update import checks every overwrite like an add
author: Pablo
date: 2026-11-05
branch: worktree-agent-ac1468cf4dbaead85
cost: { minutes: 9, tokens: 10676 }
id: l-c51dbb93
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/ImportPlanner.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Import.java
---

## Decided
- ImportPlanner.Run.entry(kind, name, service, matches, same) gives the action for every kind: add when nothing matches, skip with "It is the same already." when the values are equal, else update in mode update and skip with "It exists already." in mode add. Each branch then plans a write only when the action is not skip.
- In update mode, a changed Route Profile goes into the same RouteProfileEditor.save as the added ones, so RouteProfileChecks.check sees it. A changed Alias has its target looked up as for an add, because the overwrite points it somewhere new. Equal entries are still not checked: they are already on this Workstation.
- An update of a Static Instance is Registrations.register with the same host and port, which keeps the ID. An update of a Publication is Publications.restore with scope, priority and profiles all set, so nothing of the old one stays. publication: none on a Published Service is an update that runs Publications.unpublish.
- A Service entry stays a skip with "It exists already." in update mode, because a Service has nothing of its own to overwrite.
- The CLI sums up as "Added A, updated U, skipped S." and leaves out "updated" when U is 0, so the add-mode line did not change.
