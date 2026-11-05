---
title: "A Route Profile save is one Consul transaction, and only own keys make a profile known"
author: Pablo
date: 2026-11-05
branch: worktree-agent-a20cf54002ffa66b0
cost: { minutes: 39, tokens: 58832 }
id: l-aaee5484
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/profiles/RouteProfileEditor.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/profiles/RouteProfileChecks.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/consul/Consul.java
---

## Decided
- RouteProfileEditor.save refuses a save that needs more than Consul.TRANSACTION_SIZE (64) writes, the rewritten chains included, with TooManyRouteProfileWritesException (400). Consul.putValues sends one transaction only. Splitting into several transactions was not all or nothing: a failure in the second one left half a set in KV.
- RouteProfileChecks.check knows a stored profile only by its own key. A chain's value also holds copies of its members, and such a copy can outlive its profile, so a new chain must not name it.
- save re-checks every stored chain that the set does not name, and refuses the whole save with those problems when one fails. So a broken stored chain blocks every save until someone fixes or replaces that chain in the same set.

## Watch out
- The page controller catches TooManyRouteProfileWritesException itself, as it does for RefusedRouteProfilesException. RouteProfileProblems covers only the API controller.
