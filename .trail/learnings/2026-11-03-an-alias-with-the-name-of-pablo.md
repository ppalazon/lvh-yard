---
title: "An Alias with the name of a published Service is its last fallback, not hidden"
author: Pablo
date: 2026-11-03
branch: worktree-agent-a61fb5a45562d230d
cost: { minutes: 8, tokens: 9215 }
id: l-7d913af3
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/YardReader.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Aliases.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Routing.java
---

## Decided
- An Alias whose name matches a published Service of this Workstation has the new state `fallback`, not `hidden`. It gets no alias_ router of its own: its Remote Service's passing, in-range Instances become the `alias` kind of that Service's Routing. `hidden` now means only that the Service's Instance Priority leaves out `alias` (ticket 03 can set that).
- YardReader adds the Alias's Instances to this Workstation's own Services (withAliases), after it has read the remotes, so yard() reuses that read. services(ws) and service(ws, name) for this Workstation read the Remote Service once more; any fault there means no fallback, so it never breaks own reads.
- YardReader reads the Alias keys with the static Aliases.targets(consul). Aliases depends on YardReader, so YardReader cannot take the Aliases bean.
- Routing keeps the routed Instances (servers, hidden from JSON), because an Alias's Instances are not among the Service's own. RouteBuilder reads them from there.
- When no kind passes, the Route uses only the Service's own in-range Instances. If it has none, it gets no Route: the Alias kind only ever counts passing Instances, as the spec says.

## Watch out
- A Remote Service Instance that passes, for tests: register it at 10.9.8.7 (in range here) and remove its check in the remote Consul (remote.deregisterCheckInConsul). An Instance with no check is passing. The remote dashboard does not put back a Static Instance's check.
- Publishing needs the Service to exist, so a test of "published with no Instance" registers one, publishes, then removes it.

In the code: YardReader.withAliases, Service.withAlias, Routing.of, Aliases.alias, InstancePriorityAliasTest.
