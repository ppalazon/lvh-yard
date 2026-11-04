---
title: "lvh-yard init asks for the Workstation name and Base Domain, and Peers come later"
author: Pablo
date: 2026-11-04
branch: main
cost: { minutes: 13, tokens: 88561 }
id: l-cc833651
scope: repo
status: active
replaces: l-67e19575
src: agent
touches:
  - ".scratch/init/issues/06-init-asks-less-and-compose-runs-in-the-folder.md"
  - compose.yaml
---

## Decided
- The user asked init to ask less. At a terminal, init asks for the Workstation name and the Base Domain, each with a default that Enter accepts. It no longer asks for Peers: joining a Yard is an advanced step, done later with `lvh-yard init --reinit --peers <ip> --gossip-key <key>`.
- The default name is the computer's hostname: the part before the first dot, lowercase, invalid characters made `-`, trimmed, at most 63. Without a terminal, --workstation stays required, because two teammates can share a hostname.
- --reinit asks nothing: it keeps the old name and Base Domain unless a flag replaces them.
- Answers still come through one BufferedReader on System.in for the whole run, and the hidden --terminal option still fakes a terminal in tests. A hidden --hostname option fakes the hostname.
- `lvh-yard compose <args>` runs `docker compose <args>` in the Workstation Folder, because a program cannot change its shell's directory (no `cd`). It has no options of its own, so --help reaches Docker.

## Watch out
- Consul keeps its first gossip key in its data volume and then ignores a new -encrypt. compose.yaml passes -disable-keyring-file, so the key in .env always wins and joining later works.
- lvh-yard compose finds only the standard folder (YARD_CONFIG_DIR, XDG, ~/.config). For a --dir folder, init's next steps print `YARD_CONFIG_DIR=<dir> lvh-yard compose up -d`.

In the code: cli/.../Init.java (askWorkstation, askBaseDomain, nameFromHostname, nextSteps), cli/.../Compose.java, Yard.commandLine() setStopAtUnmatched for compose, compose.yaml consul command.
