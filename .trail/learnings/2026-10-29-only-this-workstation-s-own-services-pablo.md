---
title: "Only this Workstation's own Services can be changed, so the API's write paths do not name a Workstation"
author: Pablo
date: 2026-10-29
branch: main
cost: { minutes: 17, tokens: 178925 }
id: l-66628216
scope: repo
status: active
src: agent
touches:
  - cli/src/test/java/dev/palazon/lvhyard/cli/StubDashboard.java
  - README.md
---

## Decided
- The user decided the CLI changes only this Workstation's Services, so it never needs to know the Workstation's name. The REST API was reshaped for that: own Services live under /api/workstation/services (list, one Service, register and remove Static Instances); the Yard is read only at /api/services (with ?workstation= to filter) and /api/services/{workstation}/{service}.
- The old write paths with {workstation} and their 403 for Remote Services were removed, not kept beside the new ones: a Remote Service cannot be addressed for a write at all. The HTML pages still carry the Workstation in their paths, so Registrations keeps its Workstation check for them.
- `yard list` shows this Workstation's Services; `yard list --workstation <name>` another's. `yard remove` takes either --id (as yard list shows it) or --port with an optional --host, so teardown scripts mirror register.

## Watch out
- The CLI's stub dashboard (WireMock) refuses any stub, answer body or request body that openapi.yaml does not describe. Change the API, run mise run openapi, and the CLI tests then show what the CLI must follow.
- A test cannot set environment variables in its own JVM, so the YARD_DASHBOARD_URL tests run yard as a child process.

In the code: dashboard/.../services/WorkstationServicesController.java, ServicesController.java; cli/src/test/.../StubDashboard.java; cli/src/main/.../Dashboard.java.
