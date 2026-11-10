---
title: The Backup format model is one record that both export and import use
author: Pablo
date: 2026-11-10
branch: go-cli-ticket-17
id: l-b4f6e5f9
scope: repo
status: active
replaces: l-ac04a54e
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/Backup.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/BackupFormat.java
  - cli/internal/client/client.go
---

## Decided
- The package dashboard/.../backups holds the Backup format. Backup is a record of the file as written, version 1. BackupFormat.write dumps it with SnakeYAML in block style. BackupFormat.read checks only the shape: version 1, known keys at every level, value types. It throws RefusedBackupException with every BackupProblem(place, reason), such as services.grafana.static-instances[0].port. The rules of the API (names, Scope, priority kinds, Route Profile checks) are for the import to check.
- Backup.Service.publication is null when the file has no publication key, Backup.Unpublished for publication: none, else Backup.Published. Published.priority is null when the file leaves it out, and an empty list means off. StaticInstance.host is null for the LAN address.
- The export always writes scope and priority, and the check as Consul reads it back (type, path for http, interval, timeout). It leaves out empty tags, meta, route-profiles, static-instances and container-bindings. Top-level maps are always written, even when empty.
- exported-from.at is ISO_OFFSET_DATE_TIME at whole seconds, so SnakeYAML quotes it. read also takes an unquoted YAML timestamp.
- BackupExport reads Aliases.targets(), which reads only this Workstation's KV. Aliases.all() would read every remote Workstation.

## Watch out
- The CLI client sets Accept for the YAML endpoints (client.go), because Spring answers 406 when the Accept header names no media type of the endpoint.
