---
title: "Consul and the dashboard run as normal users, while Traefik stays root"
author: Pablo
date: 2026-11-02
branch: main
cost: { minutes: 8, tokens: 23475 }
id: l-15d4e146
scope: repo
status: active
replaces: l-dde2189c
src: agent
touches:
  - compose.yaml
  - ".env.example"
  - Dockerfile
  - README.md
---

## Decided
Consul runs as the image's consul user (100:1000). Its image entrypoint would switch to that user itself, but compose.yaml replaces the entrypoint with a shell, so Consul ran as root before.
The dashboard runs as YARD_UID:YARD_GID (1000:1000 by default), with YARD_DOCKER_GID, the group that owns the Docker socket, added with group_add so it can read the socket. The user asked for this. The image itself still defaults to root, so a plain docker run keeps working.
Traefik stays root. The user chose this.

## Tried and ruled out
Traefik as a non-root user with cap_add NET_BIND_SERVICE: it still gets "bind: permission denied" on port 80. Docker gives a non-root user no effective capabilities. Host networking also means the host's net.ipv4.ip_unprivileged_port_start (1024) applies, not a container one.
YARD_DOCKER_GID required with :? : Compose checks every service's variables, even one whose profile is off, so it broke docker compose for Workstations without the dashboard profile. It defaults to empty instead, and Docker then refuses to start only the dashboard: "unable to find group".
group_add: docker by name: the image has no docker group, and the socket's group ID differs per computer.

## Watch out
Consul data volumes made while Consul ran as root are owned by root. Chown them once to 100:1000, or Consul cannot write its data.
Inside the dashboard container, UID 1000 shows as "ubuntu": the eclipse-temurin image is Ubuntu, whose ubuntu user has that ID.

In the code: compose.yaml (consul `user`, dashboard `user` and `group_add`), .env.example, README.md "The dashboard from its image", Dockerfile comment.
