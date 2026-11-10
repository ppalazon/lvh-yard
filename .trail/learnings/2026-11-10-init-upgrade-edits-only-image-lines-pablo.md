---
title: "init --upgrade edits only image lines, and the init modes exclude each other"
author: Pablo
date: 2026-11-10
branch: go-cli-ticket-17
id: l-c3bd0478
scope: repo
status: active
replaces: l-d25e47ed
src: agent
touches:
  - cli/internal/command/init_upgrade.go
  - cli/internal/command/init.go
---

## Decided
- `lvh-yard init --upgrade` reads compose.yaml with composeImages, which finds each service's first `image:` line by line (a service header under the top-level `services:`) and swaps only the image value. It never reparses the YAML, so hand edits stay byte for byte.
- The new images come from composeImages(templates.Compose(version)), so --upgrade moves to exactly what a fresh init would write. No versions are listed twice.
- When consul, traefik or dashboard has no `image:` line, nothing is written and the command fails, because a half-upgraded file is harder to read than an untouched one.
- --force, --upgrade and --reinit are mutually exclusive: init.go declares them with MarkFlagsMutuallyExclusive, so Cobra refuses two of them. A new mode is one more flag in that call.
