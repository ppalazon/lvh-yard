---
title: The Backup format model is one record that both export and import use
author: Pablo
date: 2026-11-11
branch: go-cli
id: l-92fee0d7
scope: repo
status: active
replaces: l-b4f6e5f9
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/Backup.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/BackupFormat.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/BackupExport.java
  - cli/internal/client/client.go
---

## Decided
- The package dashboard/.../backups holds the Backup format. Backup is a record of the file as written, version 2 (Backup.VERSION). BackupFormat.write dumps it with SnakeYAML in block style. BackupFormat.read checks only the shape: version 2, known keys at every level, value types. It throws RefusedBackupException with every BackupProblem(place, reason). The rules of the API (names, Scope, Route Profile checks) are for the import to check.
- Backup.Service.publication is null when the file has no publication key, Backup.Unpublished for publication: none, else Backup.Published(scope, routeProfiles, accessLog). Published.scope is null when the file leaves it out. Backup.Service.instancePriority is a Boolean, null when the file leaves it out. The order of the priority is the position in Service.sources. StaticInstance.host is null for the LAN address.
- The export always writes scope and instance-priority, and the check as Consul reads it back (type, path for http, interval, timeout). It leaves out empty tags, meta, route-profiles and sources. Top-level maps are always written, even when empty.
- exported-from.at is ISO_OFFSET_DATE_TIME at whole seconds, so SnakeYAML quotes it. read also takes an unquoted YAML timestamp.
- BackupExport reads the Aliases from each Service's sources, which come from this Workstation's records only, so it reads no remote Workstation.

## Watch out
- The CLI client sets Accept for the YAML endpoints (client.go), because Spring answers 406 when the Accept header names no media type of the endpoint.
