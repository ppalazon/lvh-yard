---
title: "Compose starts Consul, then the dashboard, then Traefik, each one after the one before it is healthy"
author: Pablo
date: 2026-11-04
branch: main
cost: { minutes: 16, tokens: 80515 }
id: l-d3a5e65a
scope: repo
status: active
src: agent
touches:
  - docs/development.md
  - mise.toml
  - docs/user-guide.md
  - compose.yaml
---

## Decided
The user asked for Docker health checks so that the containers start one after the other: Consul, the dashboard, Traefik. Traefik waits for the dashboard so that its first poll finds the Routes. The dashboard dependency of Traefik is optional, so Traefik still starts after Consul when the dashboard profile is off. Tested both cases on a separate Compose project with free ports.

## Tried and ruled out
curl or wget in the dashboard health check: the dashboard image (eclipse-temurin:25-jre) has neither. Bash sends the HTTP request through /dev/tcp instead. Adding curl to the image was not needed.

## Watch out
Consul's leader is the string "" until Consul elects itself, so the check looks for a digit, not for a 200 answer.
After a restart of the Docker daemon, restart: unless-stopped starts the containers again with no order. depends_on works only for docker compose up.
The check task of mise must list compose.yaml and .env.example as sources: the CLI build copies them as templates, and TraefikAdminTest reads compose.yaml.

In the code:
- compose.yaml: healthcheck on consul (wget /v1/status/leader), dashboard (bash /dev/tcp, /actuator/health), traefik (wget /ping); depends_on with condition service_healthy, and required: false on traefik's dashboard dependency.
- mise.toml: tasks.check sources.
