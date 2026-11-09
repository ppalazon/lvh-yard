---
title: A Service that only Foreign Instances give is a normal Service
author: Pablo
date: 2026-11-09
branch: worktree-agent-aaeb82690a2a10f34
cost: { minutes: 38, tokens: 344825 }
id: l-68abff6e
scope: repo
status: active
replaces: l-2595302c
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ServiceRecords.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Services.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Registrations.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Publications.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/FirstSetup.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/ImportPlanner.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/DashboardClient.java
---

Decided: the user said a Service that Yard detects must be treated like any other Service. ServiceRecords keeps one JSON key per Service at lvh-yard/services/<svc>. ServiceRecords.serviceExists (a record, or an Instance in Consul that is not the server itself) is the one existence rule, and ServiceRecords.ensure writes the record of a Foreign-only Service on the first write that needs one: publish, register, bind, setInstancePriority and an Alias. A name with no record and no Instance is still UnknownServiceException, 404, and the 404 problem carries a service property so the CLI prints 'Create it first: lvh-yard service create <name>'. Services.create answers 409 for a Foreign-only name too. Unpublish and delete check serviceExists without writing. FirstSetup skips a name for which serviceExists is true, so a Foreign yard Instance is never published by the first setup. Watch out: the Backup import cannot call Services.create for a new Service, because BackupExport (and so the planner's current state) leaves out Services with no record, and create would now refuse a Foreign-only name; ImportPlanner uses Services.restore, which writes the record whatever Instances exist. Service.created still says whether a record exists: BackupExport filters on it, and the Service page shows the data-foreign-only note only without a record, to say why a deleted Service stays. Test helpers DashboardClient.register/bind/publish still create the Service first (409 ignored). A test that clears a Service uses DELETE /api/workstation/services/<svc>, because an empty Service stays.
