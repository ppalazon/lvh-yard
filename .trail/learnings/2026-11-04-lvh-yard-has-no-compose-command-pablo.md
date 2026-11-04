---
title: "lvh-yard has no compose command: init writes a reference setup and the CLI only talks to the dashboard"
author: Pablo
date: 2026-11-04
branch: main
cost: { minutes: 9, tokens: 45265 }
id: l-c5a622af
scope: repo
status: active
replaces: l-cc833651
src: agent
touches:
  - compose.yaml
  - docs/user-guide.md
---

## Decided
- The user found `lvh-yard compose` odd. Running LVH Yard with Compose is the user's choice. The CLI talks to the dashboard, and init writes the setup files. That is all.
- `lvh-yard init`, `--reinit` and `--upgrade` stay. The files init writes (compose.yaml, .env, route-profiles.yaml) are a reference setup that the user owns and can change or replace.
- Init's next steps say `cd <folder> && docker compose up -d`, the same form that `init --upgrade` prints.
- The CLI still finds the dashboard by `--dashboard`, `YARD_DASHBOARD_URL`, the Workstation Folder's .env, then 127.0.0.1:7780.
- At a terminal, init still asks only for the Workstation name and the Base Domain. Peers come later with `--reinit`.

## Tried and ruled out
- A `lvh-yard compose <args>` wrapper that ran docker compose in the Workstation Folder. It tied the CLI to Compose.
- A separate written contract of ports and variables for non-Compose setups. The comments in compose.yaml and .env.example are enough.

## Watch out
- Consul keeps its first gossip key in its data volume and ignores a new -encrypt. compose.yaml starts Consul with -disable-keyring-file so a key from --reinit wins.

In the code: Compose.java removed; Yard.commandLine no longer sets stopAtUnmatched; Init.nextSteps always prints the folder; docs/user-guide.md.
