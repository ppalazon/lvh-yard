---
title: "The CLI never tells the user to run mise tasks, because it is installed without this repo"
author: Pablo
date: 2026-11-03
branch: main
cost: { minutes: 2, tokens: 5083 }
id: l-f1a90621
scope: repo
status: active
src: agent
touches:
  - CLAUDE.md
  - cli/src/main/java/dev/palazon/lvhyard/cli/Dashboard.java
---

## Decided
The lvh-yard CLI is a system application. The person who runs it has the files that `lvh-yard init` wrote, but not this repo. So no CLI message, help text or hint names a `mise run` task. To start the dashboard or Consul, a message says to run `docker compose up -d` in the folder that `lvh-yard init` wrote.

## Watch out
`mise run ...` is right for docs aimed at people who work on this repo (README, CLAUDE.md). It is wrong in anything the CLI prints.

In the code: cli/src/main/java/dev/palazon/lvhyard/cli/Dashboard.java send() error messages, and YardTest.dashboardThatDoesNotAnswerIsExplained, which asserts that the message does not contain "mise".
