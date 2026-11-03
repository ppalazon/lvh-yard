---
title: "Traefik and the dashboard each have their own IPv4 bind address, and 0.0.0.0 is reached through loopback"
author: Pablo
date: 2026-11-02
branch: main
cost: { minutes: 8, tokens: 18564 }
id: l-44be6b21
scope: repo
status: active
src: agent
touches:
  - ".env.example"
  - README.md
  - compose.yaml
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/BindAddress.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/reachability/TraefikSettings.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/routes/TraefikController.java
  - dashboard/src/main/resources/application.yaml
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/BindAddressTest.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/TestcontainersConfiguration.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Yard.java
---

## Decided
- The user asked for any bind address, and one setting each: YARD_TRAEFIK_BIND_ADDRESS and YARD_DASHBOARD_BIND_ADDRESS, both 127.0.0.1 by default. One each, so Traefik can face the LAN while the dashboard, which has no login and controls Docker through its socket, stays on loopback.
- YARD_BIND_ADDRESS was already taken: it is Consul's LAN address for federation.
- IPv4 only, for now. The user dropped IPv6 (brackets in URLs, :: as wildcard) because they cannot test it. Adding it back means brackets in BindAddress and in compose.yaml's Traefik script.
- Every connection between Traefik and the dashboard follows one rule: the wildcard 0.0.0.0 is reached through 127.0.0.1, any other address as it is. Java has it in BindAddress; compose.yaml's Traefik start script repeats it in sh, because Compose cannot choose.

## Watch out
- Traefik on an address without loopback breaks the lvh.me hostnames on that computer: they resolve to 127.0.0.1. A Reachability test still passes, because it sends each hostname to Traefik's address with a Host header.
- The four connections: Traefik's HTTP provider to the dashboard (compose.yaml), the dashboard's Reachability tests to Traefik (TraefikSettings.url()), the fallback yard Route's server (TraefikController), and the yard Core Service's Instance (CoreServices). A new one needs BindAddress too.

In the code:
- dashboard BindAddress.host(); TraefikSettings(bindAddress, port); application.yaml server.address and lvh-yard.traefik.bind-address
- Checked by hand with 127.0.0.1, 0.0.0.0 and a LAN address, on spare ports in a separate compose project
