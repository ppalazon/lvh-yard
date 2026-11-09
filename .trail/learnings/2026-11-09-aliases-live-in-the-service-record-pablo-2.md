---
title: "Aliases live in the Service record, and a Backup holds each Alias in its Service's sources"
author: Pablo
date: 2026-11-09
branch: worktree-agent-a68043dbf4d606ee4
cost: { minutes: 32, tokens: 191920 }
id: l-5aa071bd
scope: repo
status: active
replaces: l-fad5abb1
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Aliases.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ServiceRecords.java
---

## Decided
An Alias is stored in its Service's record at lvh-yard/services/<svc>, as the JSON list "aliases" of source IDs <workstation>/<service>, in the order added (ServiceRecords.addAlias, removeAlias). AliasKeys, the lvh-yard/aliases/ keys, AliasesController, the Aliases page and the alias_<name> Traefik routers are gone.
Only the Home Workstation lists Alias sources. When YardReader reads its own Workstation, it adds them from record.aliases(). withAliases fills each one with the Instances of its Remote Service while that one has Yard Scope. To mark a Service empty, other Workstations still count its Aliases.
Aliases.add reads the Remote Service. It refuses a Remote Service without Yard Scope (400 at field workstation or service), a repeat (AliasExistsException, 409) and this Workstation (400 at workstation). A missing Service is 404. Aliases.restore, for Backup imports, skips the Remote Service read, because the Alias existed before.
The alias-above-source risk warning applies only with Yard Scope and Instance Priority on: with the toggle off there is no order to be above. The user has not yet decided whether to keep the toggle rule.
Backups are now version 2. An Alias is one entry of services.<svc>.sources, in its place in the order. The old part that said the version is still 1 no longer holds.

## Watch out
The Service page needs Aliases.of(service) for the reasons, so PagesController passes the Alias list to ServicePage.render, which hands it to InstanceSourcesCard.

In the code: dashboard/.../services/Aliases.java, ServiceRecords.java, YardReader.withAliases, AliasTarget.java, backups/Backup.java (Alias).
