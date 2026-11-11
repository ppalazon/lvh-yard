---
title: A Backup import plans every write before the first one
author: Pablo
date: 2026-11-10
branch: main
cost: { minutes: 8, tokens: 44191 }
id: l-4e1f8dc1
scope: repo
status: active
replaces: l-d97e70ea
src: agent
touches:
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/backups/ImportPlanner.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/backups/BackupImport.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/Publications.java
  - dashboard/src/test/java/cc/lvhy/yard/dashboard/RouteProfilesComponentTest.java
---

## Decided
- ImportPlanner checks the whole Backup and builds a plan before the first write: entries for the person, in file order, and steps that write in a fixed order. BackupImport.run plans, and applies the steps only when it is not a dry run and the plan changes something. A hook that must run before the first write, such as the safety Backup, belongs in BackupImport.run, between plan and apply.
- The planner compares against BackupExport, so the current state is in the same model as the file.
- Checks reuse the API rules without writing: Registrations.check and ContainerBindings.check. Each InvalidRegistrationException field maps to a place in the file.
- Alias targets are looked up only for Aliases to add, so an unreachable Workstation does not refuse a file whose Alias is a skip.

## Watch out
- A write that fails halfway has no dashboard test, because no black-box way makes a checked write fail on demand.

In the code: backups/ImportPlanner.java, BackupImport.java (run), BackupExport.java, services/Services.java (restore).
