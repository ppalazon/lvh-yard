# Instance Priority: prefer the copy you are working on

Status: ready-for-agent

Words follow `GLOSSARY.md` (Instance Priority, Static Instance, Bound Instance, Alias, Out-of-range Instance). Decisions follow ADR-0005 (Instance Priority, by Instance kind, chosen from Health) and ADR-0002.

## Problem Statement

A user runs `web` in Docker, bound to the Service `web`, and reaches it at `web.lvh.me`. To change its code, they start `web` in a terminal and register it as a Static Instance. Now Traefik spreads requests between the Docker copy and the terminal copy, so half the requests hit old code. When they stop the terminal copy, it stays in the Route. And if they have an Alias `web` to a teammate's `web`, their own Service hides it completely, even when their own copies are all stopped.

## Solution

Each Publication has an Instance Priority: an order of Instance kinds, by default Static, then Bound, then Alias. The Route uses only the first kind that has an Instance with passing Health, and only those passing Instances. When the terminal copy stops and its check turns critical, the Route moves to the Docker copy, then to the teammate's Service through the Alias. The order can be changed per Service in the CLI and on the Service page, and the page shows which kind the Route uses now.

A user can also turn the Instance Priority off for a Service. Then the Route uses every passing Static and Bound Instance of that Service together.

## User Stories

1. As a user, I want my Static Instance preferred over my Bound Instance by default, so that `web.lvh.me` reaches the copy I started in a terminal.
2. As a user, I want the Route to move to the Bound Instance when my Static Instance's check turns critical, so that stopping the terminal copy brings back the Docker one.
3. As a user, I want the Route to move back to the Static Instance when its check passes again, so that restarting it is enough.
4. As a user, I want an Alias with the same name as my Service to become the last fallback, so that `web.lvh.me` reaches my teammate's `web` when none of mine is healthy.
5. As a user, I want the Alias fallback used only for my own short hostname, so that other Workstations' Routes never depend on my Aliases.
6. As a user, I want all passing Instances of the chosen kind used together, so that two Docker replicas still share the load.
7. As a user, I want Instances of other kinds left out of the Route while a better kind passes, so that no request reaches old code.
8. As a user, I want a Service with no passing Instance in any kind still routed to all its Instances, as today, so that an Unhealthy Service behaves as before.
9. As a user, I want to set the order per Service, for example Bound before Static, so that I can choose which copy wins.
10. As a user, I want to leave a kind out of the order, so that, for example, the Alias is never used as a fallback.
11. As a user, I want `lvh-yard publish web --priority static,bound,alias` to set the order, so that I can do it from the terminal.
12. As a user, I want `lvh-yard publish web` without `--priority` to keep the current order, so that publishing again does not reset it, as with `--scope`.
13. As a user, I want an order with an unknown or repeated kind refused with a clear message, so that I do not save a broken order.
14. As a user, I want to edit the order on the Service page, so that I can change it with the mouse.
15. As a user, I want the Service page to show which kind the Route uses now and which are waiting, for example "routing to: Static (Bound waiting)", so that I know where requests go.
16. As a user, I want `lvh-yard list` to mark the Instances the Route uses now, so that I see it in the terminal.
17. As a user, I want the API to return the order and the kind in use for each Service, so that tools can show it.
18. As a teammate on another Workstation, I want a Yard-scoped Service routed with its Home Workstation's order, so that we all reach the same copy when we can.
19. As a teammate, I want my Workstation to skip the Home Workstation's Out-of-range Instances before choosing a kind, so that a copy on the Home's loopback never wins here and the Docker copy is used instead.
20. As a user, I want a Reachability test to say which Instances the Route uses now, so that I can tell a skipped Instance from a broken one.
21. As a user, I want every Instance to have at least a check, so that fallback always works; a Static Instance and a Bound Instance already get a TCP check by default.
22. As a user, I want the order kept when the last Instance of a kind goes away and comes back, so that I set it once.
23. As a user, I want to turn the Instance Priority off for a Service, so that my Static and Bound Instances share the requests.
24. As a user, I want `lvh-yard publish web --priority off` to turn it off, so that I can do it from the terminal.
25. As a user, I want `--priority` with kinds to turn it on again, so that I can go back to an order.
26. As a user, I want only my passing Instances routed while it is off, so that a stopped copy gets no requests.
27. As a user, I want every in-range Instance routed if none passes while it is off, so that it behaves as an Unhealthy Service.
28. As a user, I want a same-named Alias hidden while it is off, so that off never sends my requests to a teammate.
29. As a user, I want to turn it off and on from the Service page, so that I can do it with the mouse.
30. As a user, I want the Service page to show "Routing to: every passing Instance (Instance Priority off)", so that I know it is off.
31. As a user, I want `lvh-yard publish web` without `--priority` to keep it off, so that publishing again does not turn it on.

## Implementation Decisions

- The Instance Priority is stored with the Publication in the Home Workstation's Consul KV, as a list of kinds from `static`, `bound`, `alias`. Default `[static, bound, alias]` when absent, so existing Publications need no migration.
- An Instance's kind is known from its registration: a Bound Instance is registered by a Container Binding (already marked in reserved metadata); any other Instance is Static.
- The Route builder chooses, for each routed Service on each Workstation: drop Out-of-range Instances; walk the order; the first kind with at least one passing Instance wins and the Route gets only its passing Instances. `alias` is a kind only on the Home Workstation, for its own short hostname, when an active Alias of the same name exists; its Instances are the Alias's Remote Service's passing, in-range Instances. If no kind has a passing Instance, the Route gets every in-range Instance, as today.
- The choice is made by the dashboard from Consul Health each time it builds Traefik's configuration (every Traefik poll, 5s), not by Traefik's own `failover` service. Traefik's failover needs Traefik's own HTTP health checks on every backend, which would repeat Consul's checks and cannot express TCP checks.
- The Alias rule changes: today a same-named Service of this Workstation hides an Alias. Now the Alias's Route is not added on its own, but serves as the `alias` kind of that Service. An Alias with no same-named Service works as today.
- API: `PublicationRequest` gains `priority` (array of kinds, optional; absent keeps the current one). The Service model gains `priority` and `routing` (the kind in use, or none, and the Instance IDs it routes to). Documented in `openapi.yaml`.
- CLI: `publish --priority <kinds>`; `list` marks routed Instances.
- Page: the Service page shows the order with move up/down and remove controls, and the routing line.
- Off is an empty order: `priority: []` in the API and in the stored Publication. The CLI word `off` sends `[]`. An empty order means "no preference", so the API no longer refuses it. With an empty order, the Static and Bound Instances form one group. The same Health rule picks from that group. The `alias` kind is never used. The Service model's `routing` has no `kind` while off. Off is stored as JSON, like any order other than the default. So older Workstations cannot read it.
- To turn it on again, the user gives an order. The page's "Use an order" control sends the default order. The CLI takes `--priority` with kinds.

## Testing Decisions

- A good test checks only what a user could see: API answers, the Traefik configuration the dashboard serves, pages, CLI output.
- Seam 1, the dashboard, with real Consul and Docker from Testcontainers. Tests register Static and Bound Instances with checks that pass or fail (a listener the test opens and closes), then read the Traefik configuration: the preferred kind only; fallback when it turns critical; back again; all passing Instances of a kind; no passing Instance routes all; a custom order; a kind left out; the Alias fallback with the two-datacenter Yard; Out-of-range Instances skipped on the remote Workstation; the default order on a Publication written before this feature.
- One end-to-end test with a real Traefik: a hostname reaches the Static Instance, then the Bound one after the Static one stops.
- Seam 1 also covers off. A passing Static and a passing Bound Instance share the Route. A critical Instance leaves it. With no passing Instance, the Route uses all. A same-named Alias stays hidden. Publishing again keeps off.
- Seam 2, the CLI, with the stub dashboard: `--priority` sent and kept, refused orders, `list` marks, `--priority off` sends `[]`.
- The OpenAPI test covers the new fields.
- Prior art: the Traefik configuration test, the aliases test, the health checks test and the reachability test.

## Out of Scope

- Weights or an order per Instance.
- Sticky sessions.
- Using a teammate's Alias order: each Workstation uses only its own Aliases.
