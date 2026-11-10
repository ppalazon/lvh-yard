---
title: The safety Backup and the Backups folder
author: Pablo
date: 2026-11-10
branch: go-cli-ticket-17
id: l-d4aab83a
scope: repo
status: active
replaces: l-190bf015
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/BackupImport.java
  - compose.yaml
  - cli/internal/command/init.go
  - mise.toml
---

## Decided
- BackupsFolder is the one place that writes into the Backups folder (lvh-yard.backups.path, YARD_BACKUPS). write(name, text) creates a new file with mode 0600 from the start (Files.createFile with a POSIX attribute), refuses a name with a path, and throws FileAlreadyExistsException for an existing name. writeNew(suffix, text) names it <workstation>-<yyyyMMdd-HHmmss><suffix>.yaml and adds -2, -3 when a Backup of the same second has that name: the dashboard tests import many times in one second.
- BackupImport.run writes the safety Backup (BackupExport.backup() through BackupFormat.write) after the plan and before apply, only when not a dry run and plan.changes(). If the safety Backup cannot be written, nothing is imported. ImportResult.safetyBackup holds the name, left out of the JSON when null.
- compose.yaml mounts ./backups at /var/lib/lvh-yard/backups, read-write, and no longer mounts the Workstation Folder. init creates backups/ (cli/internal/command/init.go), so Docker does not create it as root.
- mise run dev:dashboard sets YARD_BACKUPS to dev/backups/<workstation>, because the default (~/.config/lvh-yard/backups) is the developer's real Workstation Folder and ws-a and ws-b would share it.

## Watch out
- Dashboard tests share one Backups folder per JVM (TestcontainersConfiguration.BACKUPS); ws-remote has its own. Tests in one JVM run one at a time, so counting the files before and after an import is safe.
