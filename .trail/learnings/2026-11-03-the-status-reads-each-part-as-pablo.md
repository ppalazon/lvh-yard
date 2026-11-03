---
title: "The status reads each part as a StatusPart bean, side by side, and a missing Peer is only a warning"
author: Pablo
date: 2026-11-03
branch: worktree-agent-a698d420089535849
cost: { minutes: 3, tokens: 26269 }
id: l-ee96e5c2
scope: repo
status: active
src: agent
touches:
  - compose.yaml
---

## Decided
- The Status module (dashboard/.../status/) is one StatusPart bean per part, ordered by @Order(StatusOrder.X). StatusReader reads them all on virtual threads with one shared 2 s deadline, and keeps the Status for 5 s behind a synchronized method, so many tabs cost one round. A part that throws or times out becomes failing with the reason; its thread is abandoned, not awaited, because a hung Docker or Consul call may ignore interrupts. To add a part (Traefik, Core Services), add a bean with StatusOrder.TRAEFIK or CORE_SERVICES; nothing else changes.
- A part is name, state, reason and details. A detail is {name, value, state?}: a plain fact (version, leader) has no state, a thing with its own state (a WAN member, a Peer) has one. Part.of takes the worst detail state. The page, the API and the CLI print details the same way, so a new part needs no new view.
- A Workstation in the WAN pool that is not alive, and a Peer from YARD_PEERS missing from the pool, are warnings, not failing: a teammate's switched-off computer must not make `lvh-yard status` exit 1 by default. --strict catches them.
- The dashboard now reads YARD_PEERS (lvh-yard.consul.peers; compose.yaml passes it). A Peer matches a WAN member by resolved IP and port (8302 when none is given).
- Docker failing says which: no file at the socket path, or a file the dashboard cannot read or write (then it names the socket's group and YARD_DOCKER_GID). The permission test uses a plain temp file with mode 000: Files.isWritable is false, as for a real socket.

## Watch out
- Status tests that need other settings restart ws-remote's dashboard with RemoteWorkstation.whileDashboardRunsWith(...). Its settings replace the usual ones of the same key: Spring joins a command-line argument given twice with a comma, which breaks a URI such as lvh-yard.docker.host.
- Both test dashboards set lvh-yard.consul.peers empty, so a developer's .env cannot add Peers to the tests.
