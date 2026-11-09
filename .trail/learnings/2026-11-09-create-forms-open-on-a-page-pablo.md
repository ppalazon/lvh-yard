---
title: "Create forms open on a page of their own, and a refused post shows the form again"
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 3, tokens: 24779 }
id: l-0e6e3ff6
scope: repo
status: active
replaces: l-85e37d8f
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/FormPage.java
---

## Decided
- The user moved the modal forms to their own pages. A refused post answers 400 with the form page again, with the input and the error at its field.
## Tried and ruled out
- Cancel with history.back(): after a refused post, the history holds the form page itself.
- /backups/new for the upload form: POST /backups/new already makes a new Backup.
- Native dialogs with a dialogs.js script: removed with this change.
## Watch out
- A form page has no refreshed region, so an SSE refresh cannot reset what you typed.

In the code: web/FormPage.java (Spec, openLink, render), NewServicePage, AddStaticInstancePage, BindContainerPage, AddAliasPage, BackupsPage.
