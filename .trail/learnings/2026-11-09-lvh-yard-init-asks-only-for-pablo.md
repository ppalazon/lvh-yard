---
title: "lvh-yard init asks only for the Workstation name and Base Domain, and writes a reference setup that the user owns"
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 3, tokens: 9695 }
id: l-8e1b1c9e
scope: repo
status: active
replaces: l-c5a622af
src: agent
touches:
  - cli/src/main/java/dev/palazon/lvhyard/cli/Init.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Reinit.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Templates.java
  - cli/pom.xml
---

## Decided
- At a terminal, init asks for the Workstation name and the Base Domain. Enter takes the default: the hostname of the computer made into a name, and lvh.me. Init does not ask for Peers or the gossip key. The user wanted a new Workstation to start with few questions. Joining a Yard is a later step: lvh-yard init --reinit --peers <ip> --gossip-key <key>.
- Without a terminal, --workstation is required, because two teammates can have the same hostname.
- All answers come through one BufferedReader for the whole run. A second reader loses the answers that the first one buffered. When the input ends before an answer, init fails with exit 2 and names the flag, so a pipe can never make it loop.
- The files that init writes are a reference setup that the user owns: compose.yaml, .env and an empty backups/ folder. The CLI only talks to the dashboard. The next steps say cd <folder> && docker compose up -d.
- --reinit sets the values in this order: the template, then the uncommented settings of the old .env, then what init finds (COMPOSE_PROFILES, id -u and -g, the group of the Docker socket), then the flags. What init finds goes only into a setting that is still commented or empty. So a flag beats an old value, and an old value that is not empty beats what init finds. An old empty YARD_DOCKER_GID= once wiped the group that init found.
- A commented #KEY= in the old .env means the default, so the new default wins.
- --reinit with no .env refuses and changes nothing. The old files move to <file>.<yyyyMMdd-HHmmss>.bak only after every flag check passes, so a usage error leaves the folder as it was.

## Tried and ruled out
- lvh-yard compose <args>, which ran docker compose in the Workstation Folder. It tied the CLI to Compose, and the user found it odd.
- Init that asks for Peers: joining a Yard is an advanced step.
- A written contract of ports and variables for setups without Compose. The comments in compose.yaml and .env.example are enough.

## Watch out
- A new template file must also be in the native reachability-metadata.json, or the native executable cannot find it.
- YardTest compares the written files line by line with compose.yaml and .env.example of the repository. A change there that Templates.compose cannot handle fails the CLI tests.

In the code:
- cli/src/main/java/dev/palazon/lvhyard/cli/Init.java: run, ask, askWorkstation, nameFromHostname, nextSteps
- cli/src/main/java/dev/palazon/lvhyard/cli/Reinit.java: carryOver, backUp
- cli/src/main/java/dev/palazon/lvhyard/cli/Templates.java, cli/pom.xml execution init-templates
- cli/src/main/resources/META-INF/native-image/dev.palazon/cli/reachability-metadata.json
