---
title: "Instance Priority is chosen by the dashboard from Consul Health, not by Traefik's failover service"
author: Pablo
date: 2026-11-03
branch: design/onboarding-and-routing
cost: { minutes: 1, tokens: 0 }
id: l-25bf8f52
scope: repo
status: active
src: agent
touches:
  - docs/adr/0005-instance-priority-by-instance-kind.md
---

## Decided
- A Route uses only the first Instance kind (default static, bound, alias) that has a passing Instance, and only those passing Instances. The dashboard picks it from Consul Health each time it builds Traefik's configuration.
- When no kind has a passing Instance, the Route keeps every in-range Instance, so an Unhealthy Service is still tried rather than given no servers.
- The alias kind applies only to the Home Workstation's own short hostname; other Workstations never use your Aliases.

## Tried and ruled out
- Traefik's failover service: agreed in the grill, dropped when writing the spec. It switches only on Traefik's own HTTP health checks, which would repeat Consul's checks and cannot express the default TCP checks of Static and Bound Instances.

## Watch out
- A switch takes up to about 15s: one 10s Consul check interval plus one 5s Traefik poll.

In the code: RouteBuilder.build (dashboard routes package), ADR-0005, .scratch/instance-priority/spec.md.
