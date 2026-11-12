---
title: "Podman is not supported yet: in rootless Podman a non-root container user cannot read its socket"
author: Pablo
date: 2026-11-02
branch: run-without-root
cost: { minutes: 10, tokens: 20182 }
id: l-c7b972a2
scope: repo
status: active
src: agent
touches:
  - config/.env.example
  - Dockerfile
  - README.md
  - config/compose.yaml
---

## Decided
LVH Yard supports Docker only for now. The user chose this after the tests below. Add Podman only when a Workstation really uses it.

## Tried and ruled out
Running the dashboard on rootless Podman with the Docker setup (user 1000:1000, group_add of the socket's group). Rootless Podman maps container root to the host user, and container UID 1000 to a sub-UID (100999 here). Its socket is owned by the host user, mode 0600, so 1000:1000 and the extra group both get no access. Root in the container (0:0) and userns keep-id both worked.

## Watch out
Podman support would need: a setting for the socket path (rootless: /run/user/<uid>/podman/podman.sock), and userns_mode keep-id or no user and group_add. Traefik on port 80 also fails rootless, unless net.ipv4.ip_unprivileged_port_start is lowered. Not tested yet: the dashboard on Podman's Docker-compatible events API, an empty group_add on Podman, and whether podman-compose gives containers the Compose labels that Container Bindings match.
A podman system service socket path must be under about 108 characters, or it fails with "bind: invalid argument".

In the code: config/compose.yaml dashboard `user`, `group_add` and the /var/run/docker.sock volume.
