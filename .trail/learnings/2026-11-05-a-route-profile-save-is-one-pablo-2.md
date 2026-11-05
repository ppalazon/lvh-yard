---
title: A Route Profile save is one Consul transaction of only the set
author: Pablo
date: 2026-11-05
branch: worktree-agent-a7585303341c994bf
cost: { minutes: 6, tokens: 892 }
id: l-cec190f0
scope: repo
status: active
replaces: l-aaee5484
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/profiles/RouteProfileEditor.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/consul/Consul.java
---

## Decided
- RouteProfileEditor.save refuses a save that needs more than Consul.TRANSACTION_SIZE (64) writes with TooManyRouteProfileWritesException (400). It counts only the profiles of the set that changed: since 2026-11-05 a chain names its members and holds no copies, so no stored chain is written again. Consul.putValues sends one transaction only. Splitting into several transactions was not all or nothing: a failure in the second one left half a set in KV.

## Watch out
- The page controller catches TooManyRouteProfileWritesException itself, as it does for RefusedRouteProfilesException. RouteProfileProblems covers only the API controller.
