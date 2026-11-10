---
title: "The dashboard names only the HTTP address of Consul and Traefik, never the tool that runs them"
author: Pablo
date: 2026-11-10
branch: main
cost: { minutes: 15, tokens: 29879 }
id: l-65f75806
scope: repo
status: active
replaces: l-e640e40d
src: agent
touches:
  - GLOSSARY.md
  - dashboard/openapi.yaml
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/BackupExport.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Instance.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/SourceKind.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Registrations.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ServiceWarning.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/YardReader.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/ServicePage.java
---

## Decided
- The user said that the dashboard talks to Consul only through its HTTP API, and that people can run Consul and Traefik with tools other than the setup that lvh-yard init writes. So dashboard text names the HTTP address and how to test it, and never a docker compose command.
- Browsers get a dashboard error page through Spring Boot's ErrorViewResolver, so /error stays and other clients still get JSON. When Consul does not answer, the page shows its address, curl <url>/v1/status/leader, and YARD_CONSUL_HTTP_PORT for another port.
- "Try again" shows only for a failed GET, because a link cannot post again. A 404 has none.

## Watch out
- The user guide can still use docker compose for the reference setup. Text that the dashboard shows cannot.
- A list item with the flex class gets no list number. A headless Chromium profile caches app.css, so use a fresh one for screenshots.

In the code: web/ErrorPage.java, Problems.java, services/ServiceWarning.java, ErrorPageTest, ForeignInstanceComponentTest.
