---
title: "The status reads each Status Part side by side, serves the last one while it refreshes, and a missing Peer is only a warning"
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 9, tokens: 79069 }
id: l-d6c40262
scope: repo
status: active
replaces: l-d34c5bda
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/status/StatusReader.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/status/ConsulStatus.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/status/Peer.java
---

## Decided
- The Status is one PartReader bean per Status Part, ordered by @Order(StatusOrder.X). StatusReader reads them all on virtual threads within one 2 s deadline. A part that throws or times out becomes failing with the reason. Its thread is abandoned, not awaited, because a hung Docker or Consul call can ignore interrupts. To add a part, add a bean with a new StatusOrder value.
- StatusReader serves the last Status at once, and reads it again in the background once it is older than 5 s, so a slow part never slows /status or /api/status. Tests that change something wait with await(), because the answer can be one round old.
- A WAN member that is not alive, and a Peer missing from the WAN pool, are warnings, not failing, because a teammate's switched-off computer must not make lvh-yard status exit 1 by default. --strict catches them.
- YARD_PEERS takes spaces or commas everywhere. A Peer without a port means 8302. Peers given by name are looked up side by side within 1 s, so a slow lookup is a warning on that Peer only.
- When Docker fails, the reason says which: no file at the socket path, or a file that the dashboard cannot read or write. Then it names the socket's group and YARD_DOCKER_GID.

## Watch out
- States that the test Yard cannot reach, such as no leader, IPv6 Peers and slow lookups, are tested in ConsulStatusTest against a stand-in Consul HTTP server.
- RemoteWorkstation.whileDashboardRunsWith replaces the usual settings of the same key, because Spring joins a command-line argument given twice with a comma, which breaks a URI.

In the code: status/StatusReader.java (PART_TIMEOUT, KEPT), StatusOrder.java, ConsulStatus.java (LOOKUP_TIMEOUT), DockerStatus.java, Peer.java.
