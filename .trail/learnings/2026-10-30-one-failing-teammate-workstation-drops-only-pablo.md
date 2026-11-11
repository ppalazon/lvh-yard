---
title: "One failing teammate Workstation drops only its own Routes, never the whole configuration"
author: Pablo
date: 2026-10-30
id: l-b61a4d1a
scope: repo
status: active
src: init-sessions
---

## Decided
- At first only timeouts and server errors dropped a remote Workstation's Routes, and any other error broke Traefik's whole configuration. The user asked that any error on one Workstation be ignored, and logged, while the other Workstations' Routes stay.
- In the code: Traefik config reads the Yard via YardReader.routableServices in TraefikController. Catch any error per remote Workstation, log a warning, and skip only its Routes. The API still reports such an error as a fault.

_From past Claude sessions here, Oct 30._
