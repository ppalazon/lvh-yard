---
title: Workstation setup stays separate from Yard membership
author: Pablo
date: 2026-11-19
branch: quality-review
cost: { minutes: 1 }
id: l-c0ad07b2
scope: repo
status: active
replaces: l-34f9eb37
src: agent
touches:
  - cli/internal/command/init.go
---

## Decided
- lvh-yard ws works only on files and docker compose, because it must repair a Workstation while the dashboard is down. Local setup is ws init. Membership is ws yard: init, share, join, standalone, peers.
- ws init never prints the Gossip Key, because its startup steps may be pasted into a chat. It makes a new key and no Peers, and it binds Consul to 127.0.0.1 where Docker allows it.
- ws yard init opens a Workstation: Consul certificate, Consul TLS, LAN address. ws yard share only writes an encrypted zip Invitation and changes nothing else. It prints the zip password only when it made a random one. ws yard join changes membership without changing the Workstation name.
- ws set traefik off stays offline too: it prints a separate dashboard command and does not remove the traefik-admin Service itself.

## Watch out
- A Workstation name is also its Consul datacenter and certificate identity (ADR-0013), so a rename needs a Backup and a fresh ws init, not a settings edit.
- Folder mode 600 removes directory traversal: Workstation Folders need 700 and files need 600.
- Never initialize a checkout as a Workstation Folder: recursive mode repair removes the executable bits of its tools.

## Tried and ruled out
- Opening the Workstation inside the first share. It hid a change of ports and TLS behind an invitation command. ADR-0017 has the reasons.
- ws enable and a systemd unit stay out of scope: restart: unless-stopped covers reboots.
- Bridge networking, a Traefik install YAML file and Consul DNS for Traefik: see ADR-0011 and ADR-0012.
