---
title: "The CLI command is lvh-yard, not yard, while Yard stays the domain word"
author: Pablo
date: 2026-11-10
branch: go-cli-ticket-17
id: l-50bd7ec4
scope: repo
status: active
replaces: l-0b87319a
src: agent
touches:
  - cli/internal/command/root.go
  - ".github/workflows/release.yml"
  - mise.toml
---

## Decided
- The user renamed the command from yard to lvh-yard: installed on a system PATH, yard is too generic a name. Older learnings that say "the yard executable" or "yard list" mean lvh-yard.
- Only the command's name changed: the executable, the release archives (lvh-yard-<version>-<platform>.tar.gz), the usage and version text, and the docs.
- Kept as they are: the domain word Yard (the yard Scope, the reserved name yard, yard.<base-domain>) and the YARD_* settings such as YARD_DASHBOARD_URL.

In the code:
- cli/internal/command/root.go: Use "lvh-yard"; the version text "lvh-yard <version>" comes from -X main.version in mise.toml (cli:build) and the Release workflow.
