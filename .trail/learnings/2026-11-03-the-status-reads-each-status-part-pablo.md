---
title: "The status reads each Status Part side by side, serves the last one while it refreshes, and a missing Peer is only a warning"
author: Pablo
date: 2026-11-03
branch: worktree-agent-aa6a5c5d989ee4e12
cost: { minutes: 22, tokens: 34377 }
id: l-d34c5bda
scope: repo
status: active
replaces: l-ee96e5c2
src: agent
touches:
  - cli/src/main/java/dev/palazon/lvhyard/cli/Status.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/consul/Consul.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/status/ConsulStatus.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/status/CoreServicesStatus.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/status/DockerStatus.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/status/Failures.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/status/PartReader.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/status/StatusOrder.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/status/StatusReader.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/status/TraefikStatus.java
---

## Decided
- The Status module (dashboard/.../status/) is one PartReader bean per Status Part, ordered by @Order(StatusOrder.X). (The interface was StatusPart; renamed so that StatusPart is only the API's name for the record Part.) StatusReader reads them all on virtual threads with one shared 2 s deadline. A part that throws or times out becomes failing with the reason; its thread is abandoned, not awaited, because a hung Docker or Consul call may ignore interrupts. To add a part, add a bean with StatusOrder.TRAEFIK or CORE_SERVICES; nothing else changes.
- StatusReader serves the last Status at once and, once it is older than 5 s, reads it again in the background (stale-while-refresh): a slow part never slows /status, /status/summary or /api/status. Only the very first read waits, behind a synchronized method. The answer is therefore up to one round old: tests that change something wait with await(), never assert on the next read.
- A part is name, state, reason and details. A detail is {name, value, state?}: a plain fact (version, leader) has no state, a thing with its own state (a WAN member, a Peer) has one. Part.of takes the worst detail state. Consul with no leader is failing, but still lists its WAN pool and Peers.
- A Workstation in the WAN pool that is not alive, and a Peer from YARD_PEERS missing from the pool, are warnings, not failing: a teammate's switched-off computer must not make `lvh-yard status` exit 1 by default. --strict catches them.
- YARD_PEERS is separated by spaces or commas everywhere: lvh-yard init, ConsulSettings and compose.yaml (tr ',' ' ', with set -f so [fd00::1]:8302 is no file pattern). Peer.parse reads <host>, <host>:<port>, [<ipv6>]:<port> and a bare IPv6; no port means 8302. A Peer matches a WAN member by InetAddress and port.
- Peers given by name are looked up side by side within 1 s (ConsulStatus.LOOKUP_TIMEOUT); a slow lookup is a warning on that Peer's line only, so it cannot eat the 2 s deadline and fail all of Consul.
- Docker failing says which: no file at the socket path, or a file the dashboard cannot read or write (then it names the socket's group and YARD_DOCKER_GID). The permission test uses a plain temp file with mode 000: Files.isWritable is false, as for a real socket. Docker is pinged (/_ping), then asked its version.

## Watch out
- States the test Yard cannot reach (no leader, IPv6 Peers, slow lookups) are tested in status/ConsulStatusTest against a stand-in Consul HTTP server; Consul's constructor is public for that.
- Status tests that need other settings restart ws-remote's dashboard with RemoteWorkstation.whileDashboardRunsWith(...). Its settings replace the usual ones of the same key: Spring joins a command-line argument given twice with a comma, which breaks a URI such as lvh-yard.docker.host.
- Both test dashboards set lvh-yard.consul.peers empty, so a developer's .env cannot add Peers to the tests.
