---
title: "lvh-yard init's templates are the repository's own files, copied in by the build"
author: Pablo
date: 2026-11-03
branch: init-01
cost: { minutes: 10, tokens: 31642 }
id: l-60292cb0
scope: repo
status: active
src: agent
touches:
  - compose.yaml
  - cli/pom.xml
---

## Decided
- compose.yaml and .env.example are copied into the CLI's classes as templates/ by maven-resources-plugin (cli/pom.xml, execution init-templates), so they cannot drift. Templates.compose() then pins the dashboard image to Version.VERSION (latest for a -SNAPSHOT) and drops `build: .`, as a Workstation folder has no sources. route-profiles.yaml is the CLI's own template, in cli/src/main/resources/templates.
- Templates.compose() throws when the dashboard image line or `build: .` is gone from compose.yaml; YardTest compares the written files line by line with ../compose.yaml and ../.env.example, so a change there that init cannot handle fails the CLI tests.
- EnvFile edits .env by line: set() uncomments `#KEY=` and keeps the comment above. COMPOSE_PROFILES=dashboard was added to .env.example commented, so init can set it and `mise run up` from the repository still starts no dashboard.
- YARD_UID/GID come from `id -u`/`id -g`; YARD_DOCKER_GID from the unix:gid of the socket. A hidden --docker-socket option lets the tests point at a temp file or a missing one.
- A missing --workstation with no terminal is a picocli ParameterException (exit 2); Init.interactive() is where prompting (ticket 02) plugs in.

## Watch out
- The native executable finds the templates only because META-INF/native-image/dev.palazon/cli/reachability-metadata.json lists each one by name; a new template must be added there.
- user.home in the native executable comes from the user database, not $HOME, so ConfigFolder reads HOME first.
