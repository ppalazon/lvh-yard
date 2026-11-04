---
title: Route Profiles reach the Route through the Service record
author: Pablo
date: 2026-11-04
branch: worktree-agent-abd081d09f62075c9
cost: { minutes: 3, tokens: 36301 }
id: l-bc30f301
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/routes/RouteBuilder.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/YardReader.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Publications.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/profiles/RouteProfiles.java
---

## Decided
- YardReader.read and YardReader.service read every Workstation's lvh-yard/route-profiles/ KV (RouteProfiles.stored) inside reading(), so a remote KV fault behaves like any remote read. Service carries profiles (JSON) and profileDefinitions (hidden), and RouteBuilder stays pure: it names each middleware <service.workstation()>-<name> and renames chain members the same way.
- So Routes of Remote Services already get their Home Workstation's profiles. Aliases do not yet: an Alias router lists no middlewares.
- RouteBuilder lists every profile the Publication names, even one the Home KV lacks, so Traefik refuses the Route (fail closed).
- RouteProfiles.refresh copies to Consul before it updates names(), so a profile that publish accepts is one a Route can read.
- A Publication stores profiles in the JSON form of its KV value (StoredPublication); with default priority and no profiles it stays plain Scope text for older Workstations.

## Watch out
- The auth warning (route-profile-auth) applies to any Scope, so the Service page warning heading is now generic (Check this Service).
