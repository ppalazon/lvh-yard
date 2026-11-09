---
title: Aliases live in the Service record
author: Pablo
date: 2026-11-09
branch: worktree-agent-a15aee34067894407
cost: { minutes: 29, tokens: 199597 }
id: l-fad5abb1
scope: repo
status: active
src: agent
touches:
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/ReachabilityIT.java
  - ".scratch/service-workflow/issues/02-order-the-instance-sources.md"
  - cli/src/main/java/dev/palazon/lvhyard/cli/Dashboard.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Priority.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Publish.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/TestReachability.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Yard.java
  - cli/src/test/java/dev/palazon/lvhyard/cli/YardTest.java
  - cli/src/test/java/dev/palazon/lvhyard/cli/YardTestSupport.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/reachability/Reachability.java
---

Decided: an Alias is stored in its Service's record at lvh-yard/services/<svc>, as the JSON list "aliases" of source IDs <workstation>/<service>, in the order added (ServiceRecords.addAlias/removeAlias). AliasKeys, the lvh-yard/aliases/ keys, AliasesController, the Aliases page and the alias_<name> Traefik routers are gone. Only the Home Workstation lists Alias sources: YardReader adds them from record.aliases() only when it reads its own Workstation, and withAliases fills each one with the Remote Service's Instances while that one has Yard Scope. Aliases.add reads the Remote Service and refuses one without Yard Scope (400 at field workstation or service), a repeat (AliasExistsException, 409) and this Workstation (400 at workstation); a missing Service is 404. Aliases.restore, for Backup imports, skips the Remote Service read, because the Alias existed before. The alias-above-source risk warning applies only with Yard Scope and Instance Priority on: with the toggle off there is no order to be above. Backups: the Alias moved inside its Service entry (services.<svc>.aliases: [{workstation, service}]) and the top-level aliases key is gone; the format version is still 1 for ticket 07. Watch out: the Service page needs Aliases.of(service) for the reasons, so PagesController passes the Alias list to ServicePage.render.
