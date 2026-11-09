---
title: "Instance Priority is chosen by the dashboard from Consul Health, over ordered Instance Sources"
author: Pablo
date: 2026-11-09
branch: worktree-agent-a68043dbf4d606ee4
cost: { minutes: 32, tokens: 191920 }
id: l-49d2d81f
scope: repo
status: active
replaces: l-25bf8f52
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Routing.java
---

## Decided
- The dashboard chooses the Instances of a Route from Consul Health each time it builds Traefik's configuration. Traefik does not choose.
- The choice runs over the Instance Sources of the Service in their stored order, not over Instance kinds. With Instance Priority on, the Route uses only the passing Instances of the first source that has one. With it off, the passing Instances of every source share the Route.
- When no source has a passing Instance, the Route uses every in-range Instance of every source. So the Route still tries an Unhealthy Service. Alias Instances count in this step too. The user has not yet decided whether to keep that.
- On any Workstation but the Home Workstation, Alias sources are left out before the choice.

## Tried and ruled out
- Traefik's failover service: it switches only on Traefik's own HTTP health checks. They repeat Consul's checks, and they cannot express the default TCP checks of Static and Bound Instances.

## Watch out
- A switch takes up to about 15 s: one 10 s Consul check interval plus one 5 s Traefik poll.

In the code: dashboard/.../services/Routing.java (Routing.of), RouteBuilder.build, ADR-0005.
