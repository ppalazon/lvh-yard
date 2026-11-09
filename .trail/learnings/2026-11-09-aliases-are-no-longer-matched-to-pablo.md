---
title: Aliases are no longer matched to a Service by name
author: Pablo
date: 2026-11-09
branch: worktree-agent-a68043dbf4d606ee4
cost: { minutes: 32, tokens: 191920 }
id: l-b84c90c8
scope: repo
status: active
replaces: l-7d913af3
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/YardReader.java
---

## Decided
Nothing in the old learning holds now. Aliases are no longer matched to a Service by name, and the `fallback` and `hidden` states are gone. An Alias is an Instance Source of one Service. Its Service record stores it, and it has its own place in the order of Instance Sources. See the learning "Aliases live in the Service record".

## Watch out
A Remote Service Instance that passes, for tests: register it at 10.9.8.7 (in range here) and publish the Remote Service with Yard Scope.

In the code: dashboard/.../services/YardReader.java (withAliases), Service.withAliases, Routing.of.
