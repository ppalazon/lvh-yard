---
title: The dashboard polls route-profiles.yaml; an unknown type breaks the file
author: Pablo
date: 2026-11-04
branch: worktree-agent-a627aed1b56c212bb
cost: { minutes: 30, tokens: 100276 }
id: l-1fc3e71d
scope: repo
status: active
replaces: l-d0e7b8b8
src: agent
touches:
  - compose.yaml
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/profiles/RouteProfilesWatcher.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/profiles/RouteProfiles.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/profiles/RouteProfileReader.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/routes/RouteBuilder.java
---

## Decided
- compose.yaml mounts the whole Workstation folder (./) read-only at /etc/lvh-yard, not the single file. An editor that saves by replacing the file gives it a new inode, and a single-file bind mount keeps showing the old one.
- The watcher polls the file's text every second instead of using file events: bind mounts and replaced files may send no event.
- A broken file writes nothing to KV and keeps the last profiles in the API list. Broken means bad YAML, a wrong shape, or an unknown middleware type (RouteProfileReader.checkTypesAreKnown, story 19): an unknown type is most likely a typo in a profile Services already use, so dropping only that profile would open them.
- A single refused profile (usersFile, forwardAuth to localhost or out of range, plugin, two types, chain outside the file or of a refused member) only drops that profile.
- The type allowlist (RouteProfileReader.TYPES) leaves out rate and request limits (rateLimit, inFlightReq) and path rewrites (addPrefix, stripPrefix*, replacePath*): the spec puts them out of scope, so they count as unknown types.
- KV value at lvh-yard/route-profiles/<name> is JSON in Traefik's http.middlewares shape: {<name>: {...}, <chain member>: {...}}, with names as in the file. RouteBuilder renames them to <home>_<name>. Not <home>-<name>: Workstation a with profile b-c and Workstation a-b with profile c would both give a-b-c. A Workstation name is a DNS label, so it never holds _.

## Watch out
- The dashboard tests share one route-profiles.yaml per JVM (TestcontainersConfiguration.ROUTE_PROFILES); a test that writes it must delete it after.
