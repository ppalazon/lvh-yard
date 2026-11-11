---
title: "Backup version 2 holds each Service's sources as one ordered list"
author: Pablo
date: 2026-11-11
branch: go-cli
id: l-3377abe7
scope: repo
status: active
replaces: l-ea9e3a85
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/Backup.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/BackupFormat.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/BackupExport.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/ImportPlanner.java
  - cli/internal/client/backup.go
---

## Decided
- A Service entry holds publication, instance-priority and sources: one list in the order of the Instance Priority, each item with one key, static-instance, container-binding or alias.
- The order is the list position, not stored IDs, because a Static Instance ID is a random UUID that a new Workstation does not share. ImportPlanner finds the new IDs when it applies the file, and sets the Instance Priority in a last step, after Aliases. Sources that the file does not name, such as Foreign Instances, go after the file's ones.
- In add mode, a different order is a skip, so adding a source to an existing Service shows "skip instance-priority: It exists already."
- An import always creates a new Service, empty ones too.
- Version 1 is refused, because no code reads it and LVH Yard is not released.

## Tried and ruled out
- An order key with source IDs beside separate lists: Static Instance IDs differ on each Workstation.

## Watch out
- The CLI's PlanEntry.Line() switches on the kind text, so a new kind needs a case there too.

In the code: backups/Backup.java (VERSION, Source), ImportPlanner.java, cli/internal/client/backup.go (PlanEntry.Line).
