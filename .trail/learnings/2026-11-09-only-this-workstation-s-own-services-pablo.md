---
title: "Only this Workstation's own Services can be changed, so the API's write paths do not name a Workstation"
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 9, tokens: 79069 }
id: l-d8f86e99
scope: repo
status: active
replaces: l-66628216
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/WorkstationServicesController.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ServicesController.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Remove.java
---

## Decided
- The user decided that the CLI changes only this Workstation's Services, so it never needs the Workstation's name. Own Services live under /api/workstation/services. The Yard is read only, at /api/services and /api/services/{workstation}/{service}.
- The old write paths with {workstation} were removed, not kept beside the new ones, because a Remote Service must not be addressable for a write at all. The HTML pages still carry the Workstation in their paths, so Registrations keeps its Workstation check for them.
- lvh-yard remove takes --id, as lvh-yard catalog --details shows it, or --port with an optional --host, so teardown scripts mirror register.

## Watch out
- The CLI's stub dashboard refuses any stub, answer or request body that openapi.yaml does not describe. After an API change, run mise run openapi, and the CLI tests then show what the CLI must follow.

In the code: services/WorkstationServicesController.java, ServicesController.java, cli/src/test/.../StubDashboard.java, cli Remove.java.
