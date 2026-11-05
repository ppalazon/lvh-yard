---
title: The CLI reads and writes Route Profiles as Traefik YAML with Jackson YAML
author: Pablo
date: 2026-11-05
branch: worktree-agent-affba0f353778d131
cost: { minutes: 21, tokens: 55399 }
id: l-10fb5b4c
scope: repo
status: active
src: agent
touches:
  - cli/src/main/java/dev/palazon/lvhyard/cli/RouteProfileFile.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Dashboard.java
  - cli/pom.xml
---

## Decided
- RouteProfileFile turns http.middlewares.<name> YAML into the set body of PUT /api/workstation/route-profiles, and back for profiles show. jackson-dataformat-yaml moved from test scope to the CLI's main code for this. The native executable builds and its tests pass with it, with no extra reachability metadata, because it reads only trees and maps, never records.
- Dashboard.reason prints each reason of a problem answer's problems member on a line of its own, else the detail. So a refused set shows one line per profile.

## Watch out
- MINIMIZE_QUOTES alone writes the string "100" as 100, so set would send a number back. ALWAYS_QUOTE_NUMBERS_AS_STRINGS keeps it a string. Strings like "true", "on", "null" and "" were already quoted. The round-trip test profilesSetTakesWhatProfilesShowPrints covers these.
