---
title: The Traefik part pings the admin entry point and reads the last fetch the dashboard records; test dashboards never reach ok overall
author: Pablo
date: 2026-11-03
branch: worktree-agent-af707da0438ccb1b8
cost: { minutes: 9, tokens: 13931 }
id: l-ea603c87
scope: repo
status: active
src: agent
touches:
  - README.md
  - cli/src/main/java/dev/palazon/lvhyard/cli/Dashboard.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Status.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Yard.java
  - cli/src/test/java/dev/palazon/lvhyard/cli/YardTest.java
  - compose.yaml
  - dashboard/openapi.yaml
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/consul/Consul.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/consul/ConsulSettings.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/docker/Docker.java
---

## Decided
- The last fetch is the time TraefikController last served /traefik/configuration, kept in memory by routes/TraefikFetches. Any GET of that path counts, a test's too. None since the dashboard started is a warning, the same as a fetch older than 15 s (TraefikStatus.STALE, three of compose.yaml's 5 s polls).
- The ping goes to TraefikSettings.adminUrl()/ping with a 1 s timeout, well inside the status's 2 s deadline. A failed ping makes the part failing, but the last fetch detail is still shown.
- The Core Services part reads each one through YardReader.service on this Workstation. A Core Service with no Instance (its Publication may stay) is missing, so failing. Health maps passing/warning/critical to ok/warning/failing.

## Watch out
- The test Traefik (TestcontainersConfiguration.traefik) now has the admin entry point and ping, on TRAEFIK_ADMIN_PORT, and the test dashboard is given that port. Not 8080: a developer's own Traefik may answer there and make the Traefik part ok by accident. ws-remote's dashboard still uses 8080 unless a test passes --lvh-yard.traefik.admin-port.
- In the dashboard tests the Core Services are always critical (Consul runs their checks inside its container), so the overall status is always failing there. A test cannot check that a warning alone makes overall a warning; the CLI tests cover overall states with the stub.
- The 'never fetched' warning is tested with a second Traefik whose provider points at a port where nothing listens, and ws-remote's dashboard pointed at its admin port. A real stale fetch (more than 15 s) is not tested: it would cost 15 s of waiting.
