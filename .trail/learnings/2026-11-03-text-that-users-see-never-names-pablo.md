---
title: "Text that users see never names mise tasks, because Workstations run LVH Yard without this repo"
author: Pablo
date: 2026-11-03
branch: main
cost: { minutes: 27, tokens: 82376 }
id: l-4607f5d5
scope: repo
status: active
replaces: l-f1a90621
src: agent
touches:
  - cli/src/main/java/dev/palazon/lvhyard/cli/Dashboard.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Init.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/ListServices.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Publish.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Remove.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/TestReachability.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Upgrade.java
  - cli/src/test/java/dev/palazon/lvhyard/cli/YardTest.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Aliases.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/CoreServiceException.java
---

## Decided
The lvh-yard CLI and the dashboard image run on Workstations that have only the files `lvh-yard init` wrote, not this repo. So no CLI message, dashboard page, status reason, Reachability error or API text names a `mise run` task. To start the dashboard, Consul or Traefik, the text says to run `docker compose up -d` in the folder that `lvh-yard init` wrote.

## Watch out
`mise run ...` is right only in docs for people who work on this repo (README development part, CLAUDE.md). The dashboard had the same mistake as the CLI: the Reachability test told users "Start it with: mise run up".

In the code: cli/src/main/java/dev/palazon/lvhyard/cli/Dashboard.java send(), and dashboard/src/main/java/dev/palazon/lvhyard/dashboard/reachability/ReachabilityTester.java request().
