---
title: The Backups page imports only the plan the person confirmed
author: Pablo
date: 2026-11-10
branch: go-cli-ticket-17
id: l-b2d40af5
scope: repo
status: active
replaces: l-696db081
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/BackupImport.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/BackupsPagesController.java
  - cli/internal/client/backup.go
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/BackupsFolder.java
---

## Decided
- The confirm form of the Backups page posts a hidden field plan: BackupImport.fingerprint(text, dry run), a SHA-256 of the file text, the mode and the plan entries. BackupImport.runConfirmed plans again inside the synchronized run and throws PlanChangedException with the new dry run when the fingerprint differs. The page then answers 409 with the new plan and a new confirm form, and nothing changes. The file text is in the fingerprint because an update can change values while the entries stay the same.
- ImportFailedException carries the safety Backup name. The 500 problem has applied and safetyBackup. The CLI reads that problem in cli/internal/client/backup.go (not in the general problemReason), and prints the detail, each applied entry as a plan line, and the safety Backup.
- PlanEntry.what() (dashboard) and PlanEntry.Line() (CLI client) give the same words, such as Static Instance 10.1.2.3:8080 of web.
- BackupsFolder.nameProblem(name) is the one name rule; it answers why a name cannot name a Backup file, or null. The upload shows that reason.

## Watch out
- Page tests must confirm as a browser does: GET ?mode= first, then post the hidden fields of that form (BackupsPageTest.confirm and fields). A POST without plan is a 400.
- A write that fails halfway still has no dashboard test; only a CLI test covers the 500 answer, through a fake dashboard.
