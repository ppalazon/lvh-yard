---
title: "The dashboard names only the HTTP address of Consul and Traefik, never the tool that runs them"
author: Pablo
date: 2026-11-08
branch: main
cost: { minutes: 15, tokens: 29879 }
id: l-e640e40d
scope: repo
status: active
replaces: l-3b481fa4
src: agent
touches:
  - GLOSSARY.md
  - dashboard/openapi.yaml
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/BackupExport.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Instance.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/InstanceKind.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Registrations.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ServiceWarning.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/YardReader.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/ServicePage.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/StaticInstancesPage.java
---

## Decided
- The user said that the dashboard talks to Consul only through its HTTP API, and that people can run Consul and Traefik with tools other than the docker compose setup that lvh-yard init writes. So dashboard text never suggests docker compose commands. It names the HTTP address and how to test it.
- Browsers get a dashboard error page through Spring Boot's ErrorViewResolver; /error stays, so other clients still get JSON. When Consul does not answer, Problems sets ErrorPage.CONSUL_URL, and the page shows the address, curl <url>/v1/status/leader, and YARD_CONSUL_HTTP_PORT for another port.
- "Try again" shows only for a failed GET. A 404 has none.

## Watch out
- The user guide may still use docker compose for the reference setup. Text the dashboard itself shows may not.
- The CLI still has docker compose hints (Dashboard.java). It came from init, but the same reasoning can apply there.
- A list item with the flex class gets no list number. A headless Chromium profile caches app.css, so use a fresh one for screenshots.

In the code:
- web/ErrorPage.java (consulSteps), Problems.unanswered, services/ServiceWarning.foreign (PUT /v1/agent/service/deregister/<id>), reachability/ReachabilityTester (Traefik text).
- Tests: web/ErrorPageTest and ForeignInstanceTest check that no docker compose text remains.
