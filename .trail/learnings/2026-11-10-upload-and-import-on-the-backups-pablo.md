---
title: Upload and import on the Backups page
author: Pablo
date: 2026-11-10
branch: main
cost: { minutes: 38, tokens: 73218 }
id: l-35ef19d4
scope: repo
status: active
replaces: l-9d914557
src: agent
touches:
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/web/BackupsPagesController.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/web/BackupsPage.java
---

## Decided
- An uploaded name is checked before it is written: no / or \, no leading dot, and it ends in .yaml or .yml. Then BackupFormat refuses a file that is not a Backup, and an existing name is refused. A refusal answers 400 with the Backups page and an alert, so the person sees why.
- MultipartFile.getOriginalFilename gives the name as the client sent it, with any folder in it, because Spring and Tomcat do not cut the path. So the page must check it.
- POST import answers 200 with the result page, not a redirect, because the plan and the safety Backup name exist only in that answer.

## Watch out
- The test helper postFile sends a file as multipart/form-data. Its ByteArrayResource must override getFilename, or Spring sends no filename.

In the code: backups/BackupsFolder.java (nameProblem), web/BackupsPagesController.java, DashboardClient.postFile.
