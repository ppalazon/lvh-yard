---
title: "An Alias is a key in this Workstation's Consul KV, and its state comes from the Yard as the Routes see it"
author: Pablo
date: 2026-10-30
branch: main
cost: { minutes: 1, tokens: 1711 }
id: l-a69549bc
scope: repo
status: active
src: agent
---

## Decided
- An Alias is the key lvh-yard/aliases/<alias> in this Workstation's own Consul KV. Its value is plain text: <workstation>/<service>. No other Workstation reads it, so no other one routes it.
- The API is PUT, GET and DELETE under /api/workstation/aliases, like the other write paths: it never names this Workstation. PUT answers the Alias with its state, so `yard alias add` can say whether it is routed.
- Adding an Alias checks the name with the Service name rules (Registrations.checkName("Alias", name)), refuses this Workstation as the target (400), and needs the Remote Service to exist now: 404 when the Workstation or Service is unknown, 503 when the Workstation is unreachable. This stops typos, as publishing does. The target does not need Yard Scope yet.
- Each Alias has a state worked out from the Yard: active, unavailable (Yard Scope but no Instance in range), hidden (a published own Service has its name and wins), not-shared (unpublished or Local Scope), unreachable or missing. Every state but active has a reason in words.
- The state is worked out from YardReader.routableYard(), the same read Traefik uses. With yard.yard(), one bad Publication on another Workstation made the whole Aliases page and API fail with 500. routableYard() turns that into "unreachable" for that Workstation only.
- An Alias's Traefik router is named alias_<alias> and points to the Remote Service's own backend (<service>.<workstation>). No Service or Workstation name has an _, so it never clashes. It gets a router only while that backend exists, so out-of-range Instances stay unrouted.
- Aliases builds the Alias's hostname with Workstation.hostname(name), outside YardReader. This bends the "hostname rule lives in one place" decision, but both use the same Workstation method.

## Watch out
- An own Service that is published but has no Instances (Unavailable) still hides an Alias of the same name, so neither hostname answers. That follows the ticket's words ("a published Service"). Change Aliases.alias if the user wants an Unavailable Service not to hide it.
- The Aliases page does not poll yet (ticket 12).

In the code: dashboard/.../services/Aliases.java (add, alias), Alias.java, AliasesController.java; dashboard/.../routes/RouteBuilder.java (ALIAS_PREFIX); dashboard/.../web/AliasesPage.java; cli/.../Alias.java.
