---
title: "Traefik runs only on the main Workstation, and there is no Machine concept"
author: Pablo
date: 2026-10-30
standing: true
id: l-4cb7de95
scope: repo
status: active
src: init-sessions
---

## Decided
- The brief had Traefik on every machine. The user clarified that each Workstation runs its own services, Consul server and Traefik, and Workstations see each other's services through Consul.
- In the code: Each Workstation is its own Consul datacenter, joined by WAN federation (ADR-0001). Traefik reads Routes from the local dashboard (ADR-0002). Hostnames are <service>.<base-domain> locally and <service>.<workstation>.<base-domain> for remote Services (ADR-0003). See docs/adr.

_From past Claude sessions here, Oct 29._
