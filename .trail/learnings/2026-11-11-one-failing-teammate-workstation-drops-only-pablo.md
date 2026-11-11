---
title: "One failing teammate Workstation drops only its own Routes, never the whole configuration"
author: Pablo
date: 2026-11-11
branch: go-cli
id: l-1c7812e0
scope: repo
status: active
replaces: l-b61a4d1a
src: agent
touches:
  - "dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/YardReader.java#routableYard"
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/routes/TraefikController.java
---

## Decided
- At first only timeouts and server errors dropped a remote Workstation's Routes, and any other error broke Traefik's whole configuration. The user asked that any error on one Workstation be ignored, and logged, while the other Workstations' Routes stay.
- In the code: TraefikController reads the Yard through YardReader.routableYard. It catches any RuntimeException per remote Workstation, logs the warning "The Services of {} cannot be read, so it gets no Routes for now", and gives that Workstation no Services. The API still reports such an error as a fault.
