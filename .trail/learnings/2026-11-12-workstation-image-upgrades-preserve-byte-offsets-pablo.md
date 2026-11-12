---
title: Workstation image upgrades preserve byte offsets
author: Pablo
date: 2026-11-12
branch: ws-command-integration
id: l-2d612896
scope: repo
status: active
replaces: l-8e4ed23c
src: agent
touches:
  - cli/internal/command/init.go
  - cli/internal/command/init_upgrade.go
---

## Watch out
`lvh-yard ws upgrade` edits only image values in the user-owned compose.yaml by byte offsets. Parsing and serializing YAML would lose hand edits. Take image versions from the embedded template, and refuse before writing if consul, traefik or dashboard has no image value: partial upgrades are harder to repair.

Generated Consul and Traefik files are a separate ownership contract (ADR-0011): upgrade refreshes them and keeps other files. Even with current images, Consul must be recreated to load regenerated options. A plain docker compose restart or up -d does not apply those file changes.
