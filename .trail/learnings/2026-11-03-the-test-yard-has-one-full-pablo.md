---
title: The test Yard has one full .env per Workstation and gives each Workstation its own Base Domain
author: Pablo
date: 2026-11-03
branch: main
cost: { minutes: 20, tokens: 73936 }
id: l-cb23554d
scope: repo
status: active
src: agent
touches:
  - dev/ws-a.env
  - dev/ws-b.env
  - docs/development.md
  - mise.toml
---

## Decided
The user asked for test Workstation files that look like a real .env. There is no shared file for the settings that both Workstations have. Each file repeats the gossip key, the bind address and the out-of-range list, as each real Workstation has its own .env. The two Workstations use different Base Domains, so the test Yard shows how a Workstation routes a Service of another Workstation under its own Base Domain.

## Watch out
On this computer, yard.localhost resolves to ::1 first, but Traefik listens on 127.0.0.1 only. curl and browsers fall back to IPv4, but a tool without that fallback fails on the localhost names.
ws-a routes a Yard Service of ws-b as <service>.ws-b.lvh.me, not under localhost.

In the code:
- dev/ws-a.env: YARD_BASE_DOMAIN=lvh.me, pages at http://yard.lvh.me
- dev/ws-b.env: YARD_BASE_DOMAIN=localhost, pages at http://yard.localhost:8081
- mise.toml dev:up and dev:dashboard load one --env-file per Workstation
