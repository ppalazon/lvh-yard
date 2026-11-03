---
title: Changes to a Core Service are refused with 409
author: Pablo
date: 2026-11-03
branch: core-services-03
cost: { minutes: 21, tokens: 45433 }
id: l-4d7d1b6e
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/CoreServiceException.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Publications.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Registrations.java
---

- decided: CoreServiceException answers 409 Conflict with problem details (ApiProblems), not the 405 used for Remote Services. 405 says the method is not allowed on the resource, but PUT publication on a Core Service is allowed with Local Scope; only Yard Scope is refused. Pages answer 409 too (PagesController.refused).
- decided: removing or editing an Instance is refused by its lvh-yard-core meta (Registrations.checkNotCore), so an old non-core Static Instance under a reserved name can still be cleaned up. Publication and Container Binding refusals go by name (CoreServices.NAMES), because a Service stops being core when its Core Instance is gone.
- decided: Registrations.checkName reserves yard, consul and traefik with 400 'taken by a Core Service' (InvalidRegistrationException), which covers register, bind and Aliases. Publications.publish of a Core Service name skips checkName: Local or no Scope keeps it Local and answers 200.
- decided: on start, keepCorePublication puts a Core Service with Yard Scope back to Local, since Yard Scope cannot work on loopback.
- watch out: the CLI needs no code for these refusals: Dashboard.reason prints the detail and Yard exits 1. Its stub tests only pass once openapi.yaml documents the 409s.
