---
title: "LVH Yard has no Core Services: the dashboard sets up yard, consul-ui and traefik-admin once, and then they are ordinary Services"
author: Pablo
date: 2026-11-04
branch: main
cost: { minutes: 1, tokens: 2743 }
id: l-d2998a8b
scope: repo
status: active
src: agent
touches:
  - docs/adr/0006-lvh-yard-sets-up-its-own-services-once.md
---

## Decided
The user found Core Services unneeded. Status checks Consul and Traefik at the addresses from the environment variables, so a Core Services Status Part only repeats them. Routes to LVH Yard's own pages are optional. On its first start against a Consul with no setup marker, the dashboard registers yard, consul-ui and traefik-admin as Static Instances with HTTP checks and Local Scope, and then never again. A Service with one of the names that already exists is left alone. The name consul is refused for Services and Aliases, because Consul's catalog has its own server entry under that name. There is no fallback Route to the dashboard. Status Parts are Consul, Traefik and Docker.

## Tried and ruled out
Setup commands that lvh-yard init prints for the user to run: the user wanted hostnames with no extra steps. A one-time move of Core Services from before this change: the user said there is nothing released to move from.

## Watch out
Consul's own consul entry is hidden by its ID in the Consul class. Keep that.

In the code: CoreServices, CoreService, CoreServicesStatus, CoreServiceException, Registrations.checkName and keepStaticInstance, Publications.keepCorePublication, RouteBuilder.build fallback, YardReader.core, the Instance core marker. ADR docs/adr/0006-lvh-yard-sets-up-its-own-services-once.md.
