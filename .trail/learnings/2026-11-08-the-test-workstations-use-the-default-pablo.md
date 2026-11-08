---
title: "The test Workstations use the default ports plus 10000, and an old Consul volume keeps the old ports of the first setup"
author: Pablo
date: 2026-11-08
branch: main
cost: { minutes: 4, tokens: 31881 }
id: l-f0ae3d7d
scope: repo
status: active
src: agent
touches:
  - dev/ws-a.env
  - dev/ws-b.env
  - docs/development.md
---

## Decided
The user runs a real Workstation on the same computer, so dev/ws-a.env uses each default port plus 10000 (pages on 10080) and dev/ws-b.env the port of ws-a plus 10.

## Watch out
The first setup runs once per Consul data volume. After a port change, yard, consul-ui and traefik-admin keep their old ports. On this computer the old ports belong to the real Workstation, so their checks still pass and the Routes reach the wrong Consul and Traefik. Move each Instance to the new port through the dashboard API (register at the new port, then delete the old Instance), or remove the volume ws-a_consul-data.

In the code: dev/ws-a.env, dev/ws-b.env, docs/development.md (A Yard on one computer).
