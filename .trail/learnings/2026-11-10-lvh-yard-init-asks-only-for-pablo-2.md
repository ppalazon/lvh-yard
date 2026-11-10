---
title: "lvh-yard init asks only for the Workstation name and Base Domain, and writes a reference setup that the user owns"
author: Pablo
date: 2026-11-10
branch: go-cli-ticket-17
id: l-fa236ef7
scope: repo
status: active
replaces: l-b94de8b4
src: agent
touches:
  - cli/internal/command/init.go
  - cli/internal/command/init_reinit.go
---

## Decided
- At a terminal, init asks only for the Workstation name and the Base Domain, because the user wanted few questions. Enter takes the hostname made into a name, and lvh.me. Joining a Yard comes later, with --reinit --peers and --gossip-key.
- Without a terminal, --workstation is required, because two teammates can share a hostname.
- One bufio.Reader reads every answer, because a second reader loses what the first one buffered. When the input ends, init fails, so a pipe cannot make it loop.
- --reinit keeps the old compose.yaml and .env as .bak files with a time stamp, and refuses when a .bak name exists.

## Tried and ruled out
- lvh-yard compose, a wrapper for docker compose: it tied the CLI to Compose.

## Watch out
- A new template file goes into cli/internal/templates/, and mise run cli:templates copies the root files there.

In the code: cli/internal/command/init.go, init_reinit.go, cli/internal/templates/.
