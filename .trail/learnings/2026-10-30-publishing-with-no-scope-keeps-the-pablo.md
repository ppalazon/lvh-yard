---
title: "Publishing with no Scope keeps the Scope a Publication has, and every Workstation's Traefik configuration is built from the whole Yard"
author: Pablo
date: 2026-10-30
branch: main
cost: { minutes: 1, tokens: 1547 }
id: l-8ffa1f6f
scope: repo
status: active
src: agent
---

## Decided
- The user decided that publishing with no Scope (PUT .../publication with no body, `yard publish web`, the page's Publish button) gives a new Publication Local Scope and leaves an existing one's Scope alone. So publishing again never quietly stops sharing. Only an explicit scope (body {"scope": "local"}, `--scope local`) changes it back.
- The API's Service has a second hostname field, `yardHostname`: `<service>.<home>.<base-domain>`, set only with Yard Scope. YardReader computes it, so the Service page never builds a hostname itself (the "hostname rule lives in one place" decision). `hostname` is still "where this Workstation routes it": short on the Home Workstation, the yard one on others, absent for a Remote Service with Local Scope.
- RouteBuilder routes every Service that has a `hostname` and an Instance. A Remote Service's Traefik router and backend are named `<service>.<workstation>`. A Service name is one DNS label with no dot, so it never clashes with an own Service or with `yard`.
- Loopback warnings (`warnings[]` with kind `loopback-instance`) are worked out from the host text only, never by a DNS lookup: IP literals via InetAddress.ofLiteral, plus `localhost` and `*.localhost`. A hostname that only resolves to 127.0.0.1 (such as one under lvh.me) is not caught.

## Watch out
- TraefikController now reads the whole Yard on every Traefik poll. A remote Workstation that times out or answers 5xx just loses its Routes. But any other error from one remote (a 4xx, bad JSON) fails the whole /traefik/configuration answer, and then Traefik keeps its last Routes for everyone. That follows the earlier rule "any other error stays a fault". Revisit it if it bites.
- Routes to a Remote Service's loopback Instance point at this computer's loopback, not the teammate's. They are only warned about, not dropped.

In the code: dashboard/.../services/Publications.java (publish), YardReader.java (hostname, yardHostname), ServiceWarning.java; dashboard/.../routes/RouteBuilder.java (name), TraefikController.java; cli/.../Publish.java.
