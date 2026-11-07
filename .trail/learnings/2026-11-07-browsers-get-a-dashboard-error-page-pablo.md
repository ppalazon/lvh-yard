---
title: "Browsers get a dashboard error page through an ErrorViewResolver, and Consul failures show how to start Consul"
author: Pablo
date: 2026-11-07
branch: main
cost: { minutes: 12, tokens: 77924 }
id: l-3b481fa4
scope: repo
status: active
src: agent
touches:
  - cli/.factorypath
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/CatalogPage.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/AliasesPage.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/BackupsPage.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/BackupsPagesController.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/ContainersPage.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/FormPage.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/Layout.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/PagesController.java
---

## Decided
- The user saw the Whitelabel page with a 503 while Consul was down. ErrorPage implements Spring Boot's ErrorViewResolver, so Spring Boot's own /error controller stays. Browsers (Accept text/html) get the dashboard page, and other clients still get JSON.
- When Consul does not answer, Problems sets the request attribute ErrorPage.CONSUL_URL. The page then shows the docker compose commands to see, start and read the logs of Consul, and no alert, because the steps already name the address.
- The page uses only the request and the Workstation settings, so it renders while Consul or Docker is down.
- "Try again" shows only for a failed GET, because a link cannot post again. A 404 has no "Try again".

## Tried and ruled out
- A custom ErrorController in place of BasicErrorController: it would also have to answer the JSON errors.

## Watch out
- A list item with the flex class gets no list number, so the steps are plain blocks.
- A headless Chromium profile keeps app.css in its cache. Use a fresh profile for screenshots after mise run css.
- The query string of the failed address is not in the error attributes, so "Try again" loads the path without it.

In the code:
- web/ErrorPage.java (resolveErrorView, render), Problems.unanswered sets ErrorPage.CONSUL_URL.
- Tests: web/ErrorPageTest (unit, no Consul), ErrorPagesTest (whole app, 404 HTML and JSON), ProblemsTest checks the attribute.
