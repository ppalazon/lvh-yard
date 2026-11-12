---
title: "Traefik and the dashboard each have their own IPv4 bind address, and 0.0.0.0 is reached through loopback"
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 9, tokens: 79069 }
id: l-2253e3da
scope: repo
status: active
replaces: l-44be6b21
src: agent
touches:
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/BindAddress.java
  - config/compose.yaml
---

## Decided
- The user asked for one bind address each: YARD_TRAEFIK_BIND_ADDRESS and YARD_DASHBOARD_BIND_ADDRESS, both 127.0.0.1 by default. There is one for each, so Traefik can face the LAN while the dashboard, which has no login and controls Docker through its socket, stays on loopback.
- YARD_BIND_ADDRESS was already taken: it is Consul's LAN address for federation.
- IPv4 only for now, because the user cannot test IPv6. Adding it back means brackets in BindAddress and in the Traefik script of config/compose.yaml.
- Every connection between Traefik and the dashboard follows one rule: the wildcard 0.0.0.0 is reached through 127.0.0.1, any other address as it is. Java has the rule in BindAddress. The Traefik start script in config/compose.yaml repeats it in sh, because Compose cannot choose.

## Watch out
- Traefik on an address without loopback breaks the lvh.me hostnames on that computer, because they resolve to 127.0.0.1. A Reachability test still passes, because it sends each hostname to Traefik's address with a Host header.
- The connections that use BindAddress: Traefik's HTTP provider to the dashboard (config/compose.yaml), the Reachability tests to Traefik (TraefikSettings), and the yard Instance that the first setup writes (FirstSetup). A new connection needs BindAddress too.

In the code: BindAddress.host(), TraefikSettings, FirstSetup, application.yaml server.address and lvh-yard.traefik.bind-address.
