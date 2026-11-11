---
title: "The Yard API separates this Workstation from the others, and plain catalog shows the whole Yard"
author: Pablo
date: 2026-11-11
branch: go-cli
id: l-436396cf
scope: repo
status: active
replaces: l-e6d6a8a6
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/YardController.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Yard.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/YardWorkstation.java
  - cli/internal/command/catalog.go
---

## Decided
- The first design returned one flat list of Workstations. It was changed so this Workstation, which always answers, differs in type from other Workstations, which can be unreachable. The user also reversed the earlier choice that the plain list command shows only local Services.
- In the code: GET /api/yard returns {home: HomeWorkstation, remotes: [YardWorkstation]}. Only YardWorkstation has `unreachable`. /api/services stays flat. Plain `lvh-yard catalog` lists the home and the remote Workstations. `lvh-yard catalog --workstation <name>` exits non-zero on an unknown name.
