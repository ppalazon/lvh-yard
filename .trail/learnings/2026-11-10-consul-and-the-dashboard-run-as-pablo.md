---
title: "Consul and the dashboard run as normal users, while Traefik stays root"
author: Pablo
date: 2026-11-10
branch: main
cost: { minutes: 8, tokens: 23475 }
id: l-6760fb83
scope: repo
status: active
replaces: l-15d4e146
src: agent
touches:
  - compose.yaml
  - ".env.example"
  - Dockerfile
  - README.md
---

## Decided
- Consul runs as the image's consul user (100:1000), because compose.yaml replaces the image entrypoint, which would switch user itself.
- The dashboard runs as YARD_UID:YARD_GID, with YARD_DOCKER_GID added through group_add, so it can read the Docker socket. The image itself still defaults to root, so a plain docker run keeps working.
- Traefik stays root, by the user's choice.

## Tried and ruled out
- Traefik as a non-root user with cap_add NET_BIND_SERVICE: it still got "bind: permission denied" on port 80, because Docker gives a non-root user no effective capabilities.
- YARD_DOCKER_GID required with :?: Compose checks the variables of every service, even one whose profile is off, so it broke Workstations without the dashboard.
- group_add: docker by name: the image has no docker group, and the socket's group differs per computer.

## Watch out
- Consul data volumes made while Consul ran as root are owned by root. Chown them once to 100:1000.

In the code: compose.yaml (consul user, dashboard user and group_add), .env.example, Dockerfile.
