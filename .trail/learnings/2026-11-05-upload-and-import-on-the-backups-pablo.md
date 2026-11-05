---
title: Upload and import on the Backups page
author: Pablo
date: 2026-11-05
branch: worktree-agent-a71ee3d37cb88abba
cost: { minutes: 38, tokens: 73218 }
id: l-9d914557
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/BackupsPagesController.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/BackupsPage.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/DashboardTest.java
---

## Decided
- BackupsPagesController.upload checks the name before BackupsFolder.write: no / or \, no leading dot, ends in .yaml or .yml. Then BackupFormat.read refuses a file that is not a Backup, and FileAlreadyExistsException refuses an existing name. A refusal answers 400 with the Backups page and a "Not uploaded" alert, so the person sees why without a redirect.
- MultipartFile.getOriginalFilename gives the name as the client sent it, with any folder in it: Spring and Tomcat do not cut the path. So the page must check it; the tests send ../x.yaml and sub\x.yaml.
- The import has two steps on one address. GET /backups/{name}/import asks for the mode; GET with ?mode= also runs BackupImport.run as a dry run and shows the plan with a POST Import form that carries the mode in a hidden field. POST imports and answers 200 with the result page (not a redirect), because the plan and the safety Backup name exist only in that answer.
- A refused file (RefusedBackupException, also from the planner) answers 400 with each BackupProblem.text and no Import form. An unknown mode is a 400 ResponseStatusException. ImportMode.of is now public for the web package.

## Watch out
- DashboardTest.postFile sends one file as multipart/form-data; the ByteArrayResource must override getFilename, or Spring sends no filename.
