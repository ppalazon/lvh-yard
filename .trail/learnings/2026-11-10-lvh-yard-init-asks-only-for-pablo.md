---
title: "lvh-yard init asks only for the Workstation name and Base Domain, and writes a reference setup that the user owns"
author: Pablo
date: 2026-11-10
branch: main
cost: { minutes: 3, tokens: 9695 }
id: l-b94de8b4
scope: repo
status: active
replaces: l-8e1b1c9e
src: agent
touches:
  - cli/src/main/java/dev/palazon/lvhyard/cli/Init.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Reinit.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Templates.java
  - cli/pom.xml
---

## Decided
- At a terminal, init asks only for the Workstation name and the Base Domain, because the user wanted few questions. Enter takes the hostname made into a name, and lvh.me. Joining a Yard comes later, with --reinit --peers and --gossip-key.
- Without a terminal, --workstation is required, because two teammates can share a hostname.
- One BufferedReader reads every answer, because a second reader loses what the first one buffered. When the input ends, init fails with exit 2, so a pipe cannot make it loop.
- --reinit sets values in this order: template, uncommented old settings, what init finds, flags. What init finds fills only empty settings, because an old empty YARD_DOCKER_GID= once wiped the found group.
- --reinit moves the old files to .bak only after every flag check, so a usage error changes nothing.

## Tried and ruled out
- lvh-yard compose, a wrapper for docker compose: it tied the CLI to Compose.

## Watch out
- A new template file must also be in the native reachability-metadata.json.

In the code: cli Init.java, Reinit.java, Templates.java, cli/pom.xml (init-templates).
