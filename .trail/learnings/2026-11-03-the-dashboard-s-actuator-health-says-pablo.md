---
title: "The dashboard's actuator health says only whether the dashboard runs; LVH Yard's wider status stays in the status API"
author: Pablo
date: 2026-11-03
branch: status
cost: { minutes: 30, tokens: 50208 }
id: l-19176693
scope: repo
status: active
src: agent
touches:
  - ".scratch/status/issues/01-status-of-consul-and-docker.md"
  - ".scratch/status/issues/02-status-of-traefik-and-core-services.md"
  - ".scratch/status/issues/03-status-dots-in-the-header.md"
  - ".scratch/status/spec.md"
  - GLOSSARY.md
  - ".env.example"
  - compose.yaml
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/consul/Consul.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/consul/ConsulSettings.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/docker/Docker.java
---

## Decided
The user asked whether the status (Consul, Traefik, Docker, Core Services) should also be exposed through Spring Boot Actuator health. It was decided not to. Actuator health keeps answering one question: does the dashboard process itself run. The status API, the /status page and `lvh-yard status` answer whether LVH Yard on this Workstation works.

## Tried and ruled out
- Health indicators for Consul, Traefik or Docker in the default health group. Consul's check on the dashboard Core Service calls that endpoint. A Docker or Traefik problem would make the dashboard critical, so the status would show two failures for one cause and point at the wrong piece. If critical Instances lose their Route, the dashboard's own hostname would stop working just when it is needed to diagnose. It would also loop: the Core Services part reads the dashboard's Health.
- A separate health group (for example /actuator/health/yard) that Consul does not check. It avoids the harm but only repeats the status API in another format. Worth it only if a tool that expects Spring's format arrives (Kubernetes probes, Spring Boot Admin); none is used here.

## Watch out
Adding any HealthIndicator bean puts it in the default group, and so into the dashboard Core Service's Consul check, unless it is excluded with management.endpoint.health.group settings.

In the code:
- The dashboard Core Service's check path is "/actuator/health" in dashboard/.../services/CoreService.java (YARD).
- The status lives in dashboard/.../status/ (PartReader beans) and is served at GET /api/status.
