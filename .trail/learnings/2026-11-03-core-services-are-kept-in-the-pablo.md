---
title: "Core Services are kept in the background on start, and a KV key says when it finished"
author: Pablo
date: 2026-11-03
branch: core-services
cost: { minutes: 1, tokens: 0 }
id: l-93add257
scope: repo
status: active
replaces: l-b9121120
src: agent
---

## Decided
- The user asked that the first try to keep the Core Services runs in the background, with a time limit, so a slow or hung Consul never holds up the dashboard's start. Before, start() made the first try itself and a hung Consul delayed the start.
- Each try has a 10 s limit, checked between Consul calls (each call already gives up after 3 s). A failed or late try is tried again every 5 s.
- When a try has finished, the dashboard writes the time (ISO-8601) at a KV key, so anyone can see that initialization finished on this start. The user asked for this.
- Core Instances keep fixed IDs lvh-yard-core-<name>, meta lvh-yard-instance=static plus lvh-yard-core=true. A teammate's Service is core when an Instance has that meta; on this Workstation it is core by name.
- The Consul server's own catalog entry (name consul, ID consul) is filtered per Instance, not by the name consul.

## Tried and ruled out
- Proving "a second start changes nothing" by a check's output or Health: Consul keeps a check's status and output when the same registration is sent again. Tests check IDs and no duplicate Instances instead.

## Watch out
- Tests can no longer assume the Core Services exist when the context is up. The test seam waits for the KV key: once before the first test, and after each restart of the remote dashboard while its Consul answers.
- The test Consul runs checks inside its container, so every Core check is critical in tests. Test that checks exist, not that they pass.

In the code:
- KV key: lvh-yard/core-services/initialized (CoreServices.INITIALIZED)
- CoreServices.keepUntilKept, TRY_LIMIT, RETRY
- Test wait: RemoteWorkstation.awaitCoreServicesKept, used by DashboardTest.connect and whileDashboardIsStopped
