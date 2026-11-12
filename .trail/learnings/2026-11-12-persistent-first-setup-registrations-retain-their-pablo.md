---
title: Persistent first setup registrations retain their original ports
author: Pablo
date: 2026-11-12
branch: ws-command-16
id: l-c05b177a
scope: repo
status: active
replaces: l-f0ae3d7d
src: agent
touches:
  - dev/workstations.sh
  - docs/development.md
---

## Watch out
The first setup runs once per Consul setup marker, not once per dashboard start. Changing ports does not move the yard, consul-ui or traefik-admin Instances. On a computer with a real Workstation, their old addresses can still pass checks while reaching the wrong services. Move the Static Instances through the dashboard API when changing ports. dev/workstations.sh gives ws-a the default ports plus 10000 and ws-b another 10 to avoid the real Workstation ports. Its consul-data/ bind mounts retain the setup marker across stops.
