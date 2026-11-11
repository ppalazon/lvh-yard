---
title: "lvh-yard ws replaces init, passes lifecycle commands to docker compose, and never calls the dashboard"
author: Pablo
date: 2026-11-11
branch: ws-command
id: l-3f4c81c0
scope: repo
status: active
replaces: l-fa236ef7
src: agent
touches:
  - cli/internal/command/init.go
  - cli/internal/command/init_reinit.go
  - cli/internal/command/init_upgrade.go
  - cli/internal/templates/templates.go
  - compose.yaml
---

## Decided
- `lvh-yard ws` is the group that changes one Workstation's files: ws init, upgrade, show, set, join, start, stop, restart, logs, and tls. It replaces the top-level init, init --upgrade and init --reinit, with no alias, because the user wanted a clean cutover. ws init still asks only for the Workstation name and the Base Domain, reads every answer with one bufio.Reader, and needs --workstation without a terminal. --dir becomes one persistent flag on ws.
- ws start, stop, restart and logs pass to docker compose with --project-directory <folder>, and print the line they run. The user asked for them, so the earlier ruling against a compose wrapper no longer holds.
- ws never calls the dashboard, so it works while the dashboard is down. That is why ws set traefik off cannot remove the traefik-admin Service: it prints lvh-yard remove traefik-admin.
- There is no ws rename. The name is the Consul datacenter, it is in every hostname and in the Workstation Certificate (ADR-0013). To rename, export a Backup, run ws init under the new name, and import it. Changing Yard is ws join with the Gossip Key.
- Traefik is a traefik Compose profile that ws init turns on with COMPOSE_PROFILES. The dashboard has its own setting, YARD_TRAEFIK_ENABLED (true by default), because it can run from the sources, outside Compose, where it cannot read COMPOSE_PROFILES. ws set traefik on|off writes both. When it is off, Status has no Traefik Status Part and the first setup skips traefik-admin.
- Consul runs as YARD_UID:YARD_GID, with its data in ./consul-data that ws init creates, not in a named volume. So ws init always fills YARD_UID and YARD_GID, not only for the dashboard profile. A named volume would need a root step to chown it, and the user chose the bind mount, like backups/.
- The template files move from the root to config/, and mise run cli:templates copies them from there. dev/compose.yaml adds the build line, and dev:up runs ws init --dir dev/ws-a and dev/ws-b.

## Tried and ruled out
- ws enable, a systemd unit ordered after network-online.target: the user dropped it for now. restart: unless-stopped already restarts the containers at boot. The unit would only add the order after the network.
- Bridge networking, a traefik.yaml, and Consul DNS for Traefik: see ADR-0011 and ADR-0012.

## Watch out
- A folder with mode 600 cannot be entered, even by its owner: it has no execute bit. The Workstation Folder is 700 and the files in it are 600. ws init, upgrade and set fix the modes of an old folder.
