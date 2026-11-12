---
title: "Each Workstation picks its own Base Domain, and a Peer is an address you join, not every other Workstation"
author: Pablo
date: 2026-11-03
branch: init
cost: { minutes: 1, tokens: 1718 }
id: l-202830f1
scope: repo
status: active
src: agent
touches:
  - GLOSSARY.md
---

## Decided
- Base Domain belongs to one Workstation, not to the Yard. Each Workstation's Traefik builds every hostname with its own Base Domain, and it may be a public domain. The user corrected the older text that said "the same on every Workstation".
- A Peer is the LAN address of another Workstation that this Workstation joins to find the Yard. Joining is one way: if ws-b lists ws-a, ws-a still learns about ws-b. The first Workstation of a Yard has no Peers.
- "Gossip Key" stays the name of the Yard's shared secret, even though "gossip" is a Consul word, so the flag and the setting keep their names.
- "Workstation Folder" is the name of the folder that lvh-yard ws init writes.

## Tried and ruled out
- Peer as "any other Workstation": that is just Workstation again, and it hides the one-way join.
- Renaming Peer to "Join Address" or Gossip Key to "Yard Key": clearer, but it breaks every existing .env.
- "Config Folder": it is a general programming word, not a domain word.

## Watch out
- A hostname built on one Workstation uses that Workstation's Base Domain. Never show it as if it works on another Workstation, because that one may use a different Base Domain.

In the code:
- GLOSSARY.md, section "Setting up a Workstation".
- Hostnames are built from the local setting in dashboard/.../Workstation.java.
- Flags: --peers / YARD_PEERS, --gossip-key / YARD_GOSSIP_KEY, --base-domain / YARD_BASE_DOMAIN.
