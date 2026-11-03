---
title: Core Services review fixes
author: Pablo
date: 2026-11-03
branch: core-services-review
cost: { minutes: 9, tokens: 9375 }
id: l-2bb9a9aa
scope: repo
status: active
src: agent
touches:
  - "dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Registrations.java#keepStaticInstance"
  - "dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/YardReader.java#core"
---

- decided: CoreService (enum) is the one source of the Core Services: name, what it is, check path and how its host and port come from the settings. Workstation.RESERVED_NAME and CoreServices.NAMES are gone; use CoreService.isNamed / named / YARD.
- decided: on this Workstation, Service.core is set by name (CoreService.isNamed), so the page still hides the controls while a Core Service's Static Instance is missing. Another Workstation's Service is core only by the lvh-yard-core meta (Registrations.isCore), because a teammate on an older version has a consul Service name with only Consul's own entry.
- decided: Registrations.keepStaticInstance also compares the check, read from /v1/agent/checks (ServiceID, Name, Interval, Timeout). Its Definition is empty, so the URL is not compared; host, port and path are compared through the registration and its meta instead. A check removed alone (PUT /v1/agent/check/deregister/service:<id>) is put back on the next start.
- watch out: in PagesController, CoreServiceException and RemoteServiceException are answered by @ExceptionHandler methods that call response.sendError, so the error page is the same one a thrown ResponseStatusException gave.
