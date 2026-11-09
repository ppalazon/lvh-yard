---
title: "Backup version 2 holds each Service's sources as one ordered list"
author: Pablo
date: 2026-11-09
branch: worktree-agent-a0b1554577da0551d
cost: { minutes: 25, tokens: 169279 }
id: l-2df4ab92
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/Backup.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/BackupFormat.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/BackupExport.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/ImportPlanner.java
---

## Decided
- Backup.VERSION is 2. A Service entry has publication, instance-priority (true/false, null when the file leaves it out) and sources: one list in the order of the Instance Priority, each item a mapping with one key static-instance, container-binding or alias. Backup.Source is a sealed interface; Service.staticInstances(), containerBindings() and aliases() filter the list.
- The order lives in the list position, not in stored IDs, because a Static Instance ID is a random UUID that a new Workstation does not share. ImportPlanner looks the new IDs up at apply time (Registrations.staticInstanceAt, now public) and calls Services.setInstancePriority in a last step, after Aliases. setPriority puts sources the file does not name, such as Foreign Instances, after the file's ones.
- The plan has an instance-priority entry (PlanEntry.Kind.INSTANCE_PRIORITY, 'Instance Priority of web'). It matches when the existing sources, by host:port, Binding ID or Alias ID, are the same list in the same order and the toggle is the same. In add mode a different one is a skip, so adding a source to an existing Service shows 'skip instance-priority: It exists already.' There is no entry with fewer than two sources, unless the file sets the toggle (or sets it off for a new Service).
- An import always creates a new Service, empty ones too. BackupExport holds only created Services: a Service that only Foreign Instances give is left out.
- Version 1 is refused as 'an older Backup format'; no code reads it, as LVH Yard is not released.

## Ruled out
- An order key with source IDs next to the old static-instances/container-bindings/aliases lists: Static Instance IDs differ on each Workstation, and references by host:port break for a Static Instance without host.

## Watch out
- The CLI's Dashboard.PlanEntry.line() switches on the kind text, so a new kind needs a case there too.
