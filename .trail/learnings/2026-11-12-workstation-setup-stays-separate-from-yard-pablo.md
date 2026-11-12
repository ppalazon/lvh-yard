---
title: Workstation setup stays separate from Yard invitations
author: Pablo
date: 2026-11-12
branch: ws-command-integration
id: l-c31be4c4
scope: repo
status: active
replaces: l-3f4c81c0
src: agent
touches:
  - cli/internal/command/init.go
  - cli/internal/command/init_upgrade.go
---

## Decided
`lvh-yard ws` works only on files and docker compose, because it must repair a Workstation while the dashboard is down. Keep local setup under `ws init` and membership under `ws yard`. Initialization must not print the Gossip Key: a person may paste its local startup steps into a chat. Only an explicit `ws yard share` prints joining credentials and certificate-transfer instructions. `ws yard join` changes membership without changing the Workstation identity. There is no old command alias.

The offline rule also keeps `ws set traefik off` from removing the traefik-admin Service itself. It prints a separate dashboard command instead.

## Watch out
A Workstation name is also its Consul datacenter and certificate identity (ADR-0013), so a rename needs a Backup and fresh initialization rather than a settings edit. Folder mode 600 removes directory traversal; Workstation Folders need 700 and files need 600. Never initialize a checkout as a Workstation Folder, because recursive mode repair removes executable bits from its tools.

## Tried and ruled out
`ws enable` and a systemd unit ordered after network-online.target remain out of scope. The user dropped the unit: `restart: unless-stopped` already covers reboots, and the unit would add only startup ordering.
Bridge networking, a Traefik install YAML file, and Consul DNS for Traefik were ruled out in ADR-0011 and ADR-0012.
