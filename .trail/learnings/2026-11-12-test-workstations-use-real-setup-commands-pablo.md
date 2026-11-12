---
title: Test Workstations use real setup commands and distinct Base Domains
author: Pablo
date: 2026-11-12
branch: ws-command-16
id: l-05300d2d
scope: repo
status: active
replaces: l-3fae8204
src: agent
touches:
  - dev/workstations.sh
  - mise.toml
  - docs/development.md
---

## Decided
Each test Workstation has its own ignored Workstation Folder, dev/ws-a/ or dev/ws-b/. dev/workstations.sh uses ws init and ws set, so development follows the same setup path as a user. The settings stay when the folder exists. The dashboard runs from sources, so the script starts Consul and Traefik with COMPOSE_PROFILES=traefik.

## Watch out
ws-a uses lvh.me and pages at http://yard.lvh.me:10080. ws-b uses localhost and pages at http://yard.localhost:10090. A remote ws-b Publication appears on ws-a as <service>.ws-b.lvh.me, not under localhost. localhost names can resolve to IPv6 first even though the test Traefik listens on IPv4 only; tools without an IPv4 fallback can fail.
