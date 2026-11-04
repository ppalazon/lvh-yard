---
title: "A Service has no core mark, on this Workstation or on a teammate's"
author: Pablo
date: 2026-11-04
branch: main
cost: { minutes: 1, tokens: 5838 }
id: l-3d25c95b
scope: repo
status: active
replaces: l-2bb9a9aa
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/YardReader.java
---

## Decided
- The CoreService enum, the core field on Service and Instance, and the core meta in Consul are gone (ADR 0006). The three Services of the first setup show as ordinary Services, also a teammate's.
- The Service page shows the same controls for every Service.
- Only the first setup knows the three names yard, consul-ui and traefik-admin.

## Watch out
- In PagesController, RemoteServiceException is still answered by an @ExceptionHandler that calls response.sendError, so the error page matches a thrown ResponseStatusException.

In the code:
- FirstSetup holds the three names. YardReader.core and Registrations.keepStaticInstance are gone
