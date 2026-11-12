---
title: "Compose starts Consul, then the dashboard, then Traefik, each one after the one before it is healthy"
author: Pablo
date: 2026-11-11
branch: go-cli
id: l-38de4790
scope: repo
status: active
replaces: l-5d3bec87
src: agent
touches:
  - Dockerfile
  - config/compose.yaml
  - mise.toml
  - cli/internal/templates/templates_test.go
  - dashboard/src/test/java/cc/lvhy/yard/dashboard/TraefikAdminIT.java
---

## Decided
The user asked for Docker health checks so that the containers start one after the other: Consul, the dashboard, Traefik. Traefik waits for the dashboard so that its first poll finds the Routes. The dashboard dependency of Traefik is optional, so Traefik still starts after Consul when the dashboard profile is off. Tested both cases on a separate Compose project with free ports.
The dashboard image installs curl for its health check. The user chose curl over a bash /dev/tcp request, because it is easier to read and helps to debug inside the container. The image grew from 371 MB to 382 MB.

## Tried and ruled out
A bash /dev/tcp request with grep for "status":"UP": it worked without curl, but it was hard to read.

## Watch out
Consul's leader is the string "" until Consul elects itself, so the check looks for a digit, not for a 200 answer.
After a restart of the Docker daemon, restart: unless-stopped starts the containers again with no order. depends_on works only for docker compose up.
The check task of mise must list config/compose.yaml and config/.env.example as sources. cli/internal/templates holds committed copies of both, refreshed by `mise run cli:templates` and compared with the originals by templates_test.go. TraefikAdminIT also reads config/compose.yaml.

In the code:
- Dockerfile: apt-get install curl in the last stage.
- config/compose.yaml: healthcheck on consul (wget /v1/status/leader), dashboard (curl -f /actuator/health), traefik (wget /ping); depends_on with condition service_healthy, and required: false on traefik's dashboard dependency.
- mise.toml: tasks.check sources, task cli:templates.
