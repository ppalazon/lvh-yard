---
title: "Aliases live in the Service record, and a Backup holds each Alias in its Service's sources"
author: Pablo
date: 2026-11-11
branch: go-cli
id: l-968932bd
scope: repo
status: active
replaces: l-5aa071bd
src: agent
touches:
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/Aliases.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/ServiceRecords.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/YardReader.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/AliasTarget.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/backups/Backup.java
---

## Decided
An Alias is stored in its Service's record at lvh-yard/services/<svc>, as the JSON list "aliases" of source IDs <workstation>/<service>, in the order added (ServiceRecords.addAlias, removeAlias). The lvh-yard/aliases/ keys, the Aliases page and the alias_<name> Traefik routers are gone.
Only the Home Workstation lists Alias sources. When YardReader reads its own Workstation, it adds them from record.aliases(). withAliases fills each one with the Instances of its Remote Service while that one has Yard Scope. To mark a Service empty, other Workstations still count its Aliases.
Aliases.add reads the Remote Service. It refuses a Remote Service without Yard Scope (400 at field workstation or service), a repeat (AliasExistsException, 409) and this Workstation (400 at workstation). A missing Service is 404. Aliases.restore, for Backup imports, skips the Remote Service read, because the Alias existed before.
The alias-above-source risk warning applies only with Yard Scope and Instance Priority on: with the toggle off there is no order to be above. The user has not yet decided whether to keep the toggle rule.
Backups are version 2. An Alias is one entry of services.<svc>.sources, in its place in the order.

## Watch out
The Service page needs Aliases.of(service) for the reasons, so PagesController passes the Alias list to ServicePage.render, which hands it to InstanceSourcesCard.

In the code: dashboard/.../services/Aliases.java, ServiceRecords.java, YardReader.withAliases, AliasTarget.java, backups/Backup.java (Alias).
