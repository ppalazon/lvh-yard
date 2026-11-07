---
title: "Create forms open on a page of their own, and Cancel is a link to the list page"
author: Pablo
date: 2026-11-07
branch: main
cost: { minutes: 29, tokens: 206932 }
id: l-85e37d8f
scope: repo
status: active
replaces: l-c3ac39d5
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/Layout.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/CatalogPageTest.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/ConsulWatcherTest.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/ContainersTest.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/EventsTest.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/ServicePageTest.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/StatusTest.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/FormPage.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/ContainersPage.java
---

## Decided
- The user asked to move the modal forms to their own pages. Each create form has a page with an empty form: /static-instances/new, /aliases/new, /containers/new, and /backups/upload. The list page links to it with a link that carries the form title.
- The forms still post where they posted before: /static-instances, /aliases, /containers/bind, /backups/upload. A refused post answers 400 with the form page again, with the input and the error at its field.
- Cancel is a plain link to the list page of the Section. The user chose this over history.back(), because after a refused post the browser history holds the form page itself.
- The submit button comes before Cancel, so Enter in a field sends the form.
- /aliases/new sends the browser to /aliases when no Remote Service has Yard Scope, because that page says why no Alias can be added.

## Tried and ruled out
- /backups/new for the upload form: POST /backups/new already makes a new Backup.
- Native dialogs with dialogs.js (the earlier design): removed with this change.

## Watch out
- The bind form page has no refreshed region, so an SSE refresh cannot reset what is typed.
- No test covers the /aliases/new redirect, because the tests share one Consul and other tests leave shared Services.

In the code:
- FormPage.Spec(section, title, description, path, action, submit), FormPage.openLink, FormPage.render. Cancel uses spec.section().path.
- Tests: DashboardTest.formTo(page, action) and DashboardTest.assertFormPage(list, path, title, action).
