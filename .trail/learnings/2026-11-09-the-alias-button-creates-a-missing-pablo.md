---
title: The Alias button creates a missing Service and keeps an existing one
author: Pablo
date: 2026-11-09
branch: worktree-agent-aaeb82690a2a10f34
cost: { minutes: 39, tokens: 344825 }
id: l-07efc0d9
scope: repo
status: active
replaces: l-9028625f
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Aliases.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/AliasRemoteServicePage.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Alias.java
---

Decided: the Alias button and lvh-yard alias add share one path, Aliases.add(service, workstation, remoteService, create). With create true and a missing Service (no record and no Instance), it writes the record and publishes the Service with Local Scope, but only after every check passes, so a refused Alias leaves no Service behind. An existing Service keeps its Publication, and that includes a Service that only Foreign Instances give, because the user said a Service Yard detects is like any other: it only gets its record (ServiceRecords.ensure), not a Publication. Without create, a missing Service is a 404, but a Foreign-only one takes the Alias. Aliases.of answers an empty list for a Foreign-only Service with no record, and Aliases.remove answers UnknownAliasException for it. The REST API takes create as an optional field of AliasRequest. The CLI always sends create: true and uses send, not attach. The catalog form is AliasRemoteServicePage at /services/{ws}/{remoteService}/alias/new, posting to /services/{ws}/{remoteService}/alias; Cancel goes to the catalog. Ruled out: /aliases/new, because AliasesOnTheServicePageComponentTest asserts that the old Aliases page paths answer 404. Watch out: refusals of the name show at field name, and AliasExistsException also shows at name; refusals of the workstation or service show at the read-only field target.
