---
title: The dashboard polls route-profiles.yaml through a folder mount
author: Pablo
date: 2026-11-04
branch: worktree-agent-a64a249710969665b
cost: { minutes: 7, tokens: 63162 }
id: l-d0e7b8b8
scope: repo
status: active
src: agent
touches:
  - compose.yaml
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/profiles/RouteProfilesWatcher.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/profiles/RouteProfiles.java
---

## Decided
- compose.yaml mounts the whole Workstation folder (./) read-only at /etc/lvh-yard, not the single file. An editor that saves by replacing the file gives it a new inode, and a single-file bind mount keeps showing the old one.
- The watcher polls the file's text every second instead of using file events: bind mounts and replaced files may send no event.
- A broken file (bad YAML, wrong shape) writes nothing to KV and keeps the last profiles in the API list. A single refused profile (unknown type, usersFile, forwardAuth to localhost or out of range, plugin, chain outside the file) only drops that profile.
- KV value at lvh-yard/route-profiles/<name> is JSON in Traefik's http.middlewares shape: {<name>: {...}, <chain member>: {...}}, with names as in the file. Readers rename them to <home>-<name>.

## Watch out
- The dashboard tests share one route-profiles.yaml per JVM (TestcontainersConfiguration.ROUTE_PROFILES); a test that writes it must delete it after.
