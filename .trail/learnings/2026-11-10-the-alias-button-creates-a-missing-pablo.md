---
title: The Alias button creates a missing Service and keeps an existing one
author: Pablo
date: 2026-11-10
branch: go-cli-ticket-17
id: l-c0b93b94
scope: repo
status: active
replaces: l-07efc0d9
src: agent
touches:
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/Aliases.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/web/AliasRemoteServicePage.java
  - cli/internal/command/alias.go
---

Decided: the Alias button and lvh-yard alias add share one path, Aliases.add(service, workstation, remoteService, create). With create true and a missing Service (no record and no Instance), it writes the record and publishes the Service with Local Scope, but only after every check passes, so a refused Alias leaves no Service behind. An existing Service keeps its Publication, and that includes a Service that only Foreign Instances give, because the user said a Service Yard detects is like any other: it only gets its record (ServiceRecords.ensure), not a Publication. Without create, a missing Service is a 404, but a Foreign-only one takes the Alias. Aliases.of answers an empty list for a Foreign-only Service with no record, and Aliases.remove answers UnknownAliasException for it. The REST API takes create as an optional field of AliasRequest. The CLI always sends create: true. The catalog form is AliasRemoteServicePage at /services/{ws}/{remoteService}/alias/new, posting to /services/{ws}/{remoteService}/alias; Cancel goes to the catalog. Ruled out: /aliases/new, because AliasesOnTheServicePageComponentTest asserts that the old Aliases page paths answer 404. Watch out: refusals of the name show at field name, and AliasExistsException also shows at name; refusals of the workstation or service show at the read-only field target.
