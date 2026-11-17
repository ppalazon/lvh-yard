---
title: A ws command without --dir changes your real Workstation
author: Pablo
date: 2026-11-17
branch: main
id: l-9f90129e
scope: repo
status: active
src: agent
touches:
  - cli/internal/command/workstation_unix.go
---

## Watch out
- The default Workstation Folder follows XDG_CONFIG_HOME, so ws init, ws set, ws yard share and ws yard standalone without --dir change the real folder in ~/.config/lvh-yard. Changing HOME does not help when XDG_CONFIG_HOME is set, because the CLI reads XDG_CONFIG_HOME first.
- A manual run or an agent run must pass --dir with a temporary folder every time. Tests are safe because the harness never reads the real folder.
- ws yard standalone replaces the Gossip Key and deletes tls/consul/. The old key is stored nowhere else, because only an Invitation zip made earlier still holds it. A run on the real folder fails the Yard of the Invitations that were already sent.

## Tried and ruled out
- Setting HOME to a temporary folder for a manual run. It failed because XDG_CONFIG_HOME was still set, so the CLI used the real folder.
