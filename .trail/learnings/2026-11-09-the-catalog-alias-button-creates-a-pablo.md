---
title: The catalog Alias button creates a missing Service
author: Pablo
date: 2026-11-09
branch: worktree-agent-ae79cb1db6a1cef0e
cost: { minutes: 24, tokens: 118587 }
id: l-9028625f
scope: repo
status: active
src: agent
touches:
  - ".scratch/service-workflow/issues/03-aliases-belong-to-a-service.md"
  - ".scratch/service-workflow/issues/05-static-instances-on-the-service-page.md"
  - ".scratch/service-workflow/issues/06-container-selector-on-the-service-page.md"
  - cli/src/main/java/dev/palazon/lvhyard/cli/Alias.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Dashboard.java
  - cli/src/test/java/dev/palazon/lvhyard/cli/YardTest.java
  - dashboard/openapi.yaml
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/Backup.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/BackupExport.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/BackupFormat.java
---

Decided: the Alias button and lvh-yard alias add share one path, Aliases.add(service, workstation, remoteService, create). With create true and no Service record, it creates the record and publishes the Service with Local Scope, but only after every check passes, so a refused Alias leaves no Service behind. An existing Service keeps its Publication. The REST API takes this as an optional field create in AliasRequest (POST /api/workstation/services/{service}/aliases); without it a missing Service is still a 404, because the spec says adding an Alias needs the record and the Service page form keeps that rule. The CLI always sends create: true and uses send, not attach, so the 'Create it first' message no longer applies to alias add. The catalog form is AliasRemoteServicePage at /services/{ws}/{remoteService}/alias/new, posting to /services/{ws}/{remoteService}/alias; Cancel goes to the catalog. Ruled out: /aliases/new, because AliasesOnTheServicePageComponentTest asserts that the old Aliases page paths answer 404. Watch out: refusals of the name show at field name, and AliasExistsException also shows at name; refusals of the workstation or service show at the read-only field target.
