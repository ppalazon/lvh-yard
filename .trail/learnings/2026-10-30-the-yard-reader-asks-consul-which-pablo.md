---
title: "The Yard reader asks Consul which datacenters exist, and a datacenter that times out or has no path is an unreachable Workstation"
author: Pablo
date: 2026-10-30
branch: main
cost: { minutes: 1, tokens: 1441 }
id: l-ed6a8610
scope: repo
status: active
src: agent
---

## Decided
- The user decided plain `yard list` now shows the whole Yard, grouped by Workstation, this one first. `--workstation <name>` shows one. This replaces ticket 02's "own Services only".
- GET /api/yard answers {home, remotes}. The user chose the names: `home` is a HomeWorkstation (this Workstation: name and Services, never unreachable), and `remotes` is a list of YardWorkstation (name, `unreachable`, Services), ordered by name. This replaced a first list of Workstations where "this one is first" was only implied. /api/services stays a flat list. Asking it, or /api/services/{workstation}/{service}, about an unreachable Workstation answers 503, and about a Workstation not in the Yard answers 404, so `yard list --workstation typo` exits non-zero.
- "Is this my Workstation?" has one home: Workstation.is(name). Do not compare names by hand.
- Write paths still never name a Workstation. Any POST/PUT/PATCH/DELETE under /api/services/** answers 405 with a reason that points to /api/workstation/services. The handler is @Hidden from openapi.yaml, so only the API's description mentions it.
- The Workstations of the Yard are Consul's /v1/catalog/datacenters. YARD_PEERS holds addresses, not names, so the dashboard cannot name a peer that never joined. Such a peer, or one Consul has reaped, is simply absent, not "unreachable".
- A remote datacenter counts as unreachable only on a timeout (ResourceAccessException) or a 5xx such as "No path to datacenter". Any other error stays a fault, so a bug does not look like a switched-off computer.
- Consul calls time out after 3 s, because a paused or switched-off datacenter otherwise hangs for over a minute. Consul never starts failing fast by itself: every read of a dead datacenter waited the full 3 s. So each other Workstation has a Resilience4j circuit breaker (core jar only, no Spring Boot starter). After 2 reads in a row fail, it is not asked for 10 s; then one read tries it again. Remote datacenters are also read side by side on virtual threads.
- Remote Services get no hostname here until Yard Scope exists (ticket 06).

## Watch out
- Dashboard tests now run a second Workstation, ws-remote: its own Consul, WAN-joined over a Docker network, plus a second dashboard in the same JVM (RemoteWorkstation). Its component scan must skip @TestConfiguration classes, which a plain SpringApplication does not do: Spring's TestTypeExcludeFilter is package-private, so the test has its own small TypeExcludeFilter.
- The unreachable test pauses ws-remote's Consul (docker pause) and must unpause it in finally. Every test class shares that container.
- Pages do not poll yet (ticket 12), so an open Catalog shows a returning Workstation only after a reload.

In the code: dashboard/.../services/YardReader.java (workstations, reading), ServicesController.java (change), WorkstationsController.java; dashboard/.../consul/Consul.java (ANSWER_TIMEOUT, in); dashboard/src/test/.../RemoteWorkstation.java, TestcontainersConfiguration.java; cli/.../ListServices.java.
