---
title: "The dashboard's actuator health says only whether the dashboard runs, and the wider status stays in the status API"
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 9, tokens: 79069 }
id: l-1ee3a60a
scope: repo
status: active
replaces: l-19176693
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/FirstSetup.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/status/StatusController.java
---

## Decided
The user asked whether the status (Consul, Traefik, Docker) should also go through Spring Boot Actuator health. It was decided not to. Actuator health says only whether the dashboard process runs. The status API, the /status page and lvh-yard status say whether LVH Yard on this Workstation works.

## Tried and ruled out
- Health indicators for Consul, Traefik or Docker in the default health group. The yard Service of the first setup has an HTTP check on /actuator/health. A Docker or Traefik problem would then make the dashboard critical, so the status shows two failures for one cause, and the dashboard's own hostname can lose its Route just when it is needed.
- A separate health group, such as /actuator/health/yard: it only repeats the status API in another format. It is worth it only for a tool that expects Spring's format, such as Kubernetes probes. None is used here.

## Watch out
Any new HealthIndicator bean goes into the default group, and so into the Consul check of the yard Service, unless management.endpoint.health.group settings leave it out.

In the code: FirstSetup.services (the /actuator/health check path), dashboard/.../status/ (PartReader beans), GET /api/status.
