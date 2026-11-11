---
title: "A Publication is a key in the Home Workstation's Consul KV, and YardReader gives each Service its hostname"
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 3, tokens: 24779 }
id: l-21e857f3
scope: repo
status: active
replaces: l-202f8cea
src: agent
touches:
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/Publications.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/YardReader.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/routes/RouteBuilder.java
---

## Decided
- A Publication is lvh-yard/publications/<service>, with a JSON value of the Scope, the Route Profiles and the access log. Publishing never touches an Instance.
- PUT .../publication answers with the whole Service, so lvh-yard publish prints the hostname without knowing the Base Domain.
- The state is named healthy, not published, because an Unhealthy or Unavailable Service is published too.
- YardReader gives each Service its hostname, and RouteBuilder routes that hostname, so the API and Traefik cannot drift apart.
- An Unavailable Service gets no Route, but keeps its hostname in the API and pages, so you see where it will answer.

In the code: services/Publications.java, YardReader.hostname, routes/RouteBuilder.java, WorkstationServicesController.publish.
