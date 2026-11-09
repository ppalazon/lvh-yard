---
title: "The Yard API separates this Workstation from the others, and plain catalog shows the whole Yard"
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 9, tokens: 79069 }
id: l-cafdc8d5
scope: repo
status: active
replaces: l-e6d6a8a6
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/YardController.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Catalog.java
---

## Decided
- The first design returned one flat list of Workstations. It was changed, because this Workstation always answers and the others can be unreachable, so the two differ in type. GET /api/yard returns {home: HomeWorkstation, remotes: [YardWorkstation]}, and only YardWorkstation has unreachable.
- Plain lvh-yard catalog shows the whole Yard. The user reversed an earlier choice that showed only local Services.
- /api/services stays a flat list. An unknown Workstation answers 404, so lvh-yard catalog --workstation <typo> exits non-zero.

In the code: services/YardController.java, ServicesController.java, cli Catalog.java.
