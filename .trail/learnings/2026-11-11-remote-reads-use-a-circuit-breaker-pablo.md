---
title: Remote reads use a circuit breaker so a switched-off Workstation does not slow every page
author: Pablo
date: 2026-11-11
branch: go-cli
id: l-efdd4292
scope: repo
status: active
replaces: l-8d6ee766
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/YardReader.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/consul/ConsulSettings.java
---

## Decided
- With a Workstation paused, every page load waited the full 3 second Consul timeout, because Consul never fails fast by itself. A circuit breaker skips it for a while.
- In the code: Resilience4j wraps each remote Workstation's Consul reads in YardReader. After two failed reads in a row it is not asked for 10 s (ConsulSettings.unreachablePause). DashboardIT sets the read timeout to 1 s and the pause to 2 s, so the pause-and-recover test waits seconds, not the defaults.
