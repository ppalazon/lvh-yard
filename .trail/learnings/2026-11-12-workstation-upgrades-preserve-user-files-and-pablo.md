---
title: Workstation upgrades preserve user files and refresh owned files
author: Pablo
date: 2026-11-12
branch: ws-command-16
id: l-8e4ed23c
scope: repo
status: active
replaces: l-c3bd0478
src: agent
touches:
  - cli/internal/command/init_upgrade.go
  - config/compose.yaml
---

## Watch out
`lvh-yard ws upgrade` changes only image values in the user-owned Workstation Folder compose.yaml. It keeps the other bytes instead of parsing and serializing YAML, so hand edits survive. Image versions come from the embedded template, not a second version list. Missing images for consul, traefik or dashboard cause a refusal before files change, because a partial upgrade is harder to repair.

The image-only rule does not apply to generated files. Upgrade refreshes consul.d/00-lvh-yard.hcl and installed TLS configuration, while keeping other user files. Keep CA keys outside container mounts. Use ws init --force to replace a setup, ws set for settings, and ws join for Yard membership.
