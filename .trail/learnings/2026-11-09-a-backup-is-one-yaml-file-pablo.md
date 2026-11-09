---
title: "A Backup is one YAML file per Workstation that the dashboard checks and applies, and the CLI only carries"
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 1, tokens: 10019 }
id: l-b26184dc
scope: repo
status: active
replaces: l-b00ec441
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/BackupFormat.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/ImportPlanner.java
  - compose.yaml
---

## Decided
- The dashboard parses, checks and applies a Backup, all or nothing. The CLI only sends or receives the YAML, so the rules live in one place.
- There is no --replace mode, only add and --update.
## Tried and ruled out
- Route Profiles in a file that an import writes: the dashboard sees the Workstation Folder read-only, a rewrite loses comments, and hand edits race with imports.
## Watch out
- The Backups folder needs its own read-write mount, because the Workstation Folder is mounted read-only.

In the code: backups/BackupFormat.java, ImportPlanner.java, compose.yaml (the backups volume), cli Export.java and Import.java.
