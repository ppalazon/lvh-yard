---
title: Remote reads use a circuit breaker so a switched-off Workstation does not slow every page
author: Pablo
date: 2026-10-30
id: l-8d6ee766
scope: repo
status: active
src: init-sessions
---

## Decided
- With a Workstation paused, every page load waited the full 3 second Consul timeout, because Consul never fails fast by itself. A circuit breaker skips it for a while.
- In the code: Resilience4j wraps each remote Workstation's Consul reads. After two failed reads in a row it is not asked for 10 s. The pause-and-recover test takes about 16 s because of that wait.

_From past Claude sessions here, Oct 30._
