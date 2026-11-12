---
title: Private Workstation folders require matching container users
author: Pablo
date: 2026-11-12
branch: ws-command-16
id: l-e532363e
scope: repo
status: active
replaces: l-6760fb83
src: agent
touches:
  - config/compose.yaml
  - cli/internal/command/init.go
---

## Decided
Consul and the dashboard run as YARD_UID:YARD_GID. ws init creates consul-data/ before Docker starts, because the Workstation Folder is private to its owner and Docker must not create root-owned bind mounts. The dashboard adds YARD_DOCKER_GID through group_add to read the Docker socket. Traefik stays root by the user choice.

## Tried and ruled out
Traefik as a non-root user with NET_BIND_SERVICE still failed to bind port 80 because Docker gave that user no effective capabilities. A required YARD_DOCKER_GID broke setups without the dashboard because Compose checks variables even for inactive profiles. A group named docker cannot work across computers because the socket group differs and the image has no such group.
