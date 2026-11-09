---
title: A Service record under lvh-yard/services/ makes a Service exist
author: Pablo
date: 2026-11-09
branch: worktree-agent-a8737865606bafa0a
cost: { minutes: 3, tokens: 34523 }
id: l-2595302c
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ServiceRecords.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Services.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Registrations.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/FirstSetup.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Dashboard.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/DashboardClient.java
---

Decided: ServiceRecords keeps one JSON key per Service at lvh-yard/services/<svc> (value {} for now), with all(datacenter) for remote reads, so ticket 02 can add the order and the Instance Priority toggle to ServiceRecord. YardReader shows a Service while it has a record or any Instance; a Publication or a Container Binding alone no longer makes it exist. Registrations.checkCreated is the one 404 rule for register and bind, and it runs after the request checks, so a bad body is still a 400 on a missing Service (HealthChecksComponentTest posts to missing Services). Publications.addPublishingNew and the publish field of the requests are gone. Creating a record for a name that only Foreign Instances give is allowed, so you can publish it; only an existing record is a 409. FirstSetup skips a name that has a record or any Instance in Consul, else a Foreign yard Instance got published. The 404 problem carries a service property, which the CLI uses to print 'Create it first: lvh-yard service create <name>', because register container can get its Service name from the dashboard. Watch out: DashboardClient.register/bind/bindCompose/publish and RemoteWorkstation.register/bind/publish now create the Service first (409 ignored); tests that POST straight to instancesPath must call create() first. A test that cleared a Service by removing its parts now uses DELETE /api/workstation/services/<svc>, because an empty Service stays. Backups: the import creates the record of each new Service in its own step before the Instances, but an empty Service still exports as publication none and the import skips it; ticket 07 must handle that.
