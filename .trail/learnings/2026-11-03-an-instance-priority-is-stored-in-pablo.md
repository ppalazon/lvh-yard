---
title: "An Instance Priority is stored in the Publication's KV value, as JSON only when it is not the default"
author: Pablo
date: 2026-11-03
branch: worktree-agent-a3082efda8f2f4173
cost: { minutes: 11, tokens: 39751 }
id: l-e410d4b9
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Publications.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/ServicePage.java
---

## Decided
- The Publication key lvh-yard/publications/<service> keeps the Scope alone ("local") while the order is static, bound, alias. With another order its value is JSON: {"scope":"yard","priority":["bound","static"]}. Publications.publication reads both. So a Workstation on an older version still reads a Publication with the default order. A Publication with its own order makes that older Workstation fail to read this one.
- Publishing with no priority keeps the stored order, as with scope (PUT body, lvh-yard publish without --priority, the page's Scope button, which posts only scope).
- An order must name at least one kind, and each kind once. InstanceKind.priority refuses others with InvalidRegistrationException (400, the reason as detail). A stored order that breaks these rules is a fault (IllegalStateException), not a 400.
- A Core Service refuses a priority (409), as it has only its own Instance.
- The Service page posts the whole new order from each button (Up, Down, Remove, Add) as priority=bound,static, so it needs no script. The last kind has no Remove button.

## Tried and ruled out
- A separate KV key for the order: two writes that can disagree, and one more read of every remote Workstation on each Traefik poll.

## Watch out
- The test of an old Publication writes the plain Scope straight into ws-remote's KV (remote.putConsulValue). That value is valid, so it needs no cleanup.
