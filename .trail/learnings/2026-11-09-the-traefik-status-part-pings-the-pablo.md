---
title: The Traefik Status Part pings the admin entry point and reads the last fetch that the dashboard records
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 9, tokens: 79069 }
id: l-0a1c7788
scope: repo
status: active
replaces: l-ea603c87
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/status/TraefikStatus.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/routes/TraefikFetches.java
---

## Decided
- The last fetch is the time when TraefikController last served /traefik/configuration, kept in memory by TraefikFetches. Any GET of that path counts, a test's too. No fetch since the start is a warning, the same as a fetch older than 15 s (TraefikStatus.STALE, three of Traefik's 5 s polls).
- The ping goes to the admin address /ping with a 1 s timeout, inside the 2 s deadline of the status. A failed ping makes the part failing, but the last fetch is still shown.

## Watch out
- The test Traefik has the admin entry point and ping on its own TRAEFIK_ADMIN_PORT, not 8080, because a developer's own Traefik can answer on 8080 and make the part ok by accident.
- A real stale fetch, older than 15 s, is not tested, because it costs 15 s of waiting.

In the code: routes/TraefikFetches.java, status/TraefikStatus.java (STALE), TestcontainersConfiguration.traefik.
