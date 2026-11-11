---
title: "Only this Workstation's own Services can be changed, so the API's write paths do not name a Workstation"
author: Pablo
date: 2026-11-10
branch: go-cli-ticket-17
id: l-946f4097
scope: repo
status: active
replaces: l-d8f86e99
src: agent
touches:
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/WorkstationServicesController.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/ServicesController.java
  - cli/internal/command/remove.go
  - cli/internal/client/clienttest/contract.go
---

## Decided
- The user decided that the CLI changes only this Workstation's Services, so it never needs the Workstation's name. Own Services live under /api/workstation/services. The Yard is read only, at /api/services and /api/services/{workstation}/{service}.
- The old write paths with {workstation} were removed, not kept beside the new ones, because a Remote Service must not be addressable for a write at all. The HTML pages still carry the Workstation in their paths, so Registrations keeps its Workstation check for them.
- lvh-yard remove takes --id, as lvh-yard catalog --details shows it, or --port with an optional --host, so teardown scripts mirror register.

## Watch out
- The CLI's contract helper (cli/internal/client/clienttest/contract.go) reads dashboard/openapi.yaml, so its fake dashboard refuses what the contract does not describe. After an API change, run mise run openapi, and the CLI tests then show what the CLI must follow.

In the code: services/WorkstationServicesController.java, ServicesController.java, cli/internal/command/remove.go.
