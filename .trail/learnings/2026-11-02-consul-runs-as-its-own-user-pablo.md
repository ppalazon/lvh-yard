---
title: "Consul runs as its own user, while Traefik and the dashboard stay root"
author: Pablo
date: 2026-11-02
branch: main
cost: { minutes: 2, tokens: 15487 }
id: l-dde2189c
scope: repo
status: active
src: agent
touches:
  - compose.yaml
---

## Decided
Consul runs as the image's consul user (100:1000). Its image entrypoint would switch to that user itself, but compose.yaml replaces the entrypoint with a shell, so Consul ran as root before.
Traefik and the dashboard stay root. The user chose this.

## Tried and ruled out
Traefik as a non-root user with cap_add NET_BIND_SERVICE: it still gets "bind: permission denied" on port 80. Docker gives a non-root user no effective capabilities. Host networking also means the host's net.ipv4.ip_unprivileged_port_start (1024) applies, not a container one.
Dashboard as non-root with group_add: the Docker socket's group ID differs per computer, and socket access is root on the host anyway, so it gains little.

## Watch out
Consul data volumes made while Consul ran as root are owned by root. Chown them once to 100:1000, or Consul cannot write its data.

In the code: compose.yaml, consul service `user: "100:1000"`.
