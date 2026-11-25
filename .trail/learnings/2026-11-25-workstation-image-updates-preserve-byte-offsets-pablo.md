---
title: Workstation image updates preserve byte offsets
author: Pablo
date: 2026-11-25
branch: main
id: l-0d157ca6
scope: repo
status: active
replaces: l-2d612896
src: agent
touches:
  - cli/internal/command/init.go
  - cli/internal/command/init_upgrade.go
---

`lvh-yard ws compose update` edits only image values in the user-owned compose.yaml by byte offsets. Parsing and serializing YAML would lose hand edits. Take image versions from the embedded template, and refuse before writing if consul, traefik or dashboard has no image value: partial updates are harder to repair. The Backup comes after that check and before the first write, so a refused update asks the dashboard for nothing and leaves no backups folder.

Generated Consul and Traefik files are a separate ownership contract (ADR-0011): update refreshes them and keeps other files. Even with current images, Consul must be recreated to load regenerated options. A plain docker compose restart or up -d does not apply those file changes.
