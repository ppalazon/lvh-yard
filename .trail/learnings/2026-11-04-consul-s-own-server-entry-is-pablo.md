---
title: "Consul's own server entry is hidden by its ID, and the name consul is refused"
author: Pablo
date: 2026-11-04
branch: main
cost: { minutes: 1, tokens: 5838 }
id: l-ad2300d5
scope: repo
status: active
replaces: l-b9121120
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/consul/Consul.java
---

## Decided
- No Core Services exist any more (ADR 0006), so no Service is kept on start and no Instance has a fixed core ID.
- Consul's own catalog entry (name consul, ID consul) stays hidden by its ID, per Instance.
- Because that entry uses the name consul, the dashboard refuses consul for a Service, an Alias and an Import, with 400 and the reason "consul is Consul's own entry in its catalog". The reason has no full stop. No other name is reserved.

## Watch out
- If you remove the name rule, a user's consul Instances mix with Consul's own entry.

In the code:
- Consul.isServerItself, Registrations.CONSUL and CONSUL_REFUSAL, Registrations.INVALID_NAME for the API docs
