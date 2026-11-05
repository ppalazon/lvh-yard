---
title: Upload and Alias modals
author: Pablo
date: 2026-11-05
branch: worktree-agent-a1c712648a1b04704
cost: { minutes: 8, tokens: 1037 }
id: l-038ec21c
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/BackupsPage.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/AliasesPage.java
---

Decided: BackupsPage.uploadForm shows the upload problems as an alert inside the upload-backup dialog, under the file field, and the dialog opens again when uploadProblems is not empty. The alert moved out of the top of the page, because the error belongs under its field as in the other modals. A browser never keeps a chosen file after a post, so the person chooses it again; the user guide says so. Decided: AliasesPage shows the 'No Remote Service is shared with the Yard yet' line in place of the Add an Alias button, and writes no add-alias dialog, because a form with an empty Remote Service select cannot add anything.
