---
title: A Backup import plans every write before the first one
author: Pablo
date: 2026-11-05
branch: worktree-agent-a2570c45fc325b35e
cost: { minutes: 8, tokens: 44191 }
id: l-d97e70ea
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/ImportPlanner.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/BackupImport.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Publications.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/RouteProfilesTest.java
---

## Decided
- ImportPlanner checks the whole Backup and builds an ImportPlan: entries for the person (file order) and Steps (writes, in the order Route Profiles, Static Instances and Container Bindings, Publications, Aliases). BackupImport.run reads, plans, and applies the steps only when not a dry run and plan.changes(). A hook that must run before the first write (the safety Backup) belongs in BackupImport.run, between plan and apply.
- The planner compares against BackupExport.backup(), so the current state is in the same model as the file. A host of null means the LAN address on both sides.
- Checks reuse the API rules without writing: Registrations.check(service, request), ContainerBindings.check(service, request), Registrations.checkName and InstanceKind.priority are now public. Each InvalidRegistrationException field maps to a place in the file.
- Publications.restore writes a Publication without the Service having to exist. A Backup can hold a Service that has only a Publication, and publish() refuses that Service as unknown.
- Route Profiles to add go through RouteProfileEditor.save in one step. A file Route Profile with the name of a stored one is skipped and not checked.
- Alias targets are looked up only for Aliases to add, so an unreachable Workstation does not refuse a file whose Alias is a skip.

## Watch out
- Tests in one JVM share Consul, and the import tests add Route Profiles. RouteProfilesTest read the first profile of the list. It now finds its own by name.
- A write that fails halfway has no dashboard test: no black-box way makes a checked write fail on demand.
