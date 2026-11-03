---
title: lvh-yard init --reinit carries .env values over
author: Pablo
date: 2026-11-03
branch: init-04
cost: { minutes: 9, tokens: 49542 }
id: l-d1ddfc1c
scope: repo
status: active
src: agent
touches:
  - cli/src/main/java/dev/palazon/lvhyard/cli/Init.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/EnvFile.java
---

## Decided
- Init --reinit with no .env in the folder refuses (YardException, exit 1) and touches nothing, even when compose.yaml exists: without a .env there are no values to carry, and a plain init is what the user wants. A missing compose.yaml is fine: only the files that exist are backed up.
- Order of values in Init.run(): template, then the old .env's uncommented settings (Reinit.carryOver), then what init detects (COMPOSE_PROFILES=dashboard, id -u/-g, Docker socket gid) but only into settings still commented or empty (Init.fillIfEmpty), then the flags. So a flag beats an old value, an old non-empty value beats detection, and detection beats an old empty value (an old `YARD_DOCKER_GID=` once wiped the found gid and fired the missing-socket warning). --workstation and --gossip-key fall back to the old .env; --peers replaces the old peers only when given.
- Only uncommented old settings are carried; a commented #KEY= in the old .env means default, so the new default wins. Old keys the template no longer has go at the end, commented, under '# No longer settings of lvh-yard, kept from the old .env:'.
- route-profiles.yaml is never backed up or rewritten by --reinit; it is written only when missing.
- Backups are moved (Files.move, no replace) to <file>.<yyyyMMdd-HHmmss>.bak before the new files are written, after all flag checks, so a usage error leaves the folder as it was.
