---
title: "An Instance Priority is stored in the Publication's KV value, as JSON only when it is not the default"
author: Pablo
date: 2026-11-03
branch: main
cost: { minutes: 2, tokens: 21694 }
id: l-9b4fa25d
scope: repo
status: active
replaces: l-e410d4b9
src: agent
---

## Decided
- The Publication key lvh-yard/publications/<service> keeps the Scope alone ("local") while the order is static, bound, alias. With another order, or off, its value is JSON: {"scope":"yard","priority":["bound","static"]}, or "priority":[] for off. Publications.publication reads both. So a Workstation on an older version still reads a Publication with the default order. A Publication with its own order, or off, makes that older Workstation fail to read this one.
- Publishing with no priority keeps the stored order or off, as with scope (PUT body, lvh-yard publish without --priority, the page's Scope button, which posts only scope).
- An empty order means off, and is accepted. An order must name each kind once. InstanceKind.priority refuses an unknown or repeated kind with InvalidPriorityException (400, the reason as detail). A stored order that breaks these rules is a fault (IllegalStateException), not a 400.
- A Core Service refuses a priority (409), as it has only its own Instance.
- The Service page posts the whole new order from each button (Up, Down, Remove, Add) as priority=bound,static, so it needs no script. The last kind has no Remove button, because "Turn off" posts the empty order.

## Tried and ruled out
- A separate KV key for the order: two writes that can disagree, and one more read of every remote Workstation on each Traefik poll.

## Watch out
- The test of an old Publication writes the plain Scope straight into ws-remote's KV (remote.putConsulValue). That value is valid, so it needs no cleanup.

In the code: Publications.publish, Publications.value, InstanceKind.priority, ServicePage.instancePriority, PagesController.kinds.
