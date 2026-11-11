---
title: "The status reads each Status Part side by side, serves the last one while it refreshes, and a missing Peer is only a warning"
author: Pablo
date: 2026-11-10
branch: main
cost: { minutes: 9, tokens: 79069 }
id: l-8bdde29f
scope: repo
status: active
replaces: l-d6c40262
src: agent
touches:
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/status/StatusReader.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/status/ConsulStatus.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/status/Peer.java
---

## Decided
- The Status is one PartReader bean per Status Part. StatusReader reads them on virtual threads within one 2 s deadline. A part that throws or times out becomes failing. Its thread is abandoned, because a hung Docker or Consul call can ignore interrupts.
- StatusReader serves the last Status at once, and reads it again in the background once it is older than 5 s, so a slow part never slows a page. So tests wait with await().
- A WAN member that is not alive, and a missing Peer, are warnings, because a teammate's switched-off computer must not make lvh-yard status exit 1. --strict catches them.
- Peers given by name are looked up within 1 s, so a slow lookup is a warning on that Peer only.
- When Docker fails, the reason names the missing socket, or its group and YARD_DOCKER_GID.

## Watch out
- States the test Yard cannot reach, such as no leader, are tested in ConsulStatusTest against a stand-in Consul server.
- RemoteWorkstation.whileDashboardRunsWith replaces settings of the same key, because Spring joins a repeated argument with a comma.

In the code: status/StatusReader.java, ConsulStatus.java, DockerStatus.java, Peer.java.
