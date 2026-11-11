---
title: "The Yard reader asks Consul which datacenters exist, and only a timeout or a 5xx makes a Workstation unreachable"
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 9, tokens: 79069 }
id: l-a205e9cb
scope: repo
status: active
replaces: l-ed6a8610
src: agent
touches:
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/YardReader.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/Workstation.java
---

## Decided
- The Workstations of the Yard are Consul's /v1/catalog/datacenters, because YARD_PEERS holds addresses, not names. A Peer that never joined, or one that Consul reaped, is absent, not unreachable.
- A remote datacenter counts as unreachable only on a timeout or a 5xx such as "No path to datacenter". Any other error stays a fault, so a bug does not look like a switched-off computer.
- Remote datacenters are read side by side on virtual threads.
- "Is this my Workstation?" has one home, Workstation.is(name), so names are never compared by hand.

## Watch out
- In tests, the other Workstation's dashboard runs in the same JVM. Its component scan must skip @TestConfiguration classes. Spring's TestTypeExcludeFilter is package-private, so the tests have their own small TypeExcludeFilter.
- A test that pauses the other Workstation's Consul must unpause it in finally, because every test class of the JVM shares it.

In the code: services/YardReader.java (workstations, reading), Workstation.is, the test RemoteWorkstation.java.
