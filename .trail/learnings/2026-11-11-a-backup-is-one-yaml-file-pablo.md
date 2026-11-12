---
title: "A Backup is one YAML file per Workstation that the dashboard checks and applies, and the CLI only carries"
author: Pablo
date: 2026-11-11
branch: go-cli
id: l-26e849c3
scope: repo
status: active
replaces: l-b26184dc
src: agent
touches:
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/backups/BackupFormat.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/backups/ImportPlanner.java
  - config/compose.yaml
  - cli/internal/command/backup.go
  - cli/internal/client/backup.go
---

## Decided
- The dashboard parses, checks and applies a Backup, all or nothing. The CLI only sends or receives the YAML, so the rules live in one place.
- There is no --replace mode, only add and --update.

## Tried and ruled out
- Route Profiles in a file that an import writes: a rewrite loses comments, and hand edits race with imports.

In the code: backups/BackupFormat.java, ImportPlanner.java, config/compose.yaml (the ./backups volume), cli/internal/command/backup.go and cli/internal/client/backup.go.
