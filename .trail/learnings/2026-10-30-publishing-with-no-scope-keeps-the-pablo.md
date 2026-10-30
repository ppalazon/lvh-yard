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

- The user decided that for Routes, any error reading another Workstation drops only that Workstation's Routes; the others keep theirs (YardReader.routableServices, logged as a warning). The API keeps the older rule: only a timeout or 5xx makes a Workstation unreachable, any other error stays a fault, so a bug does not look like a switched-off computer.
- The user decided that a Remote Service's Instance on loopback or a Docker bridge network is an Out-of-range Instance (glossary; first named "Unreachable Instance", renamed so it is not mixed up with an Unreachable Workstation or Reachability): `outOfRange: true` in the API, marked on the page and in `yard list`, never routed, since a Route to it would hit this computer's own loopback or containers. Own Instances are never out of range.
- The ranges are one list, YARD_OUT_OF_RANGE (default 127.0.0.0/8, ::1/128, 172.17.0.0/16 to 172.31.0.0/16; the user chose not to include 192.168.0.0/16, which most home LANs use). Loopback is in the list, not fixed, because the test Yard (dev/yard.env) runs both Workstations on one computer's loopback and must leave it out. Set but empty means nothing is out of range.

## Watch out
- The test for a fault writes a Publication with an unknown Scope straight into ws-remote's Consul KV. It must delete it in finally: while it is there, every read of ws-remote fails, in every test class.

In the code: dashboard/.../services/Publications.java (publish), YardReader.java (hostname, yardHostname), ServiceWarning.java; dashboard/.../routes/RouteBuilder.java (name), TraefikController.java; cli/.../Publish.java.
