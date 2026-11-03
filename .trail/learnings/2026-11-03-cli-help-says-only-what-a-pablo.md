---
title: "CLI help says only what a command does, in one line, and never explains what it shows"
author: Pablo
date: 2026-11-03
branch: shorter-cli-help
cost: { minutes: 11, tokens: 18983 }
id: l-d3493ba5
scope: repo
status: active
src: agent
touches:
  - cli/src/main/java/dev/palazon/lvhyard/cli/Alias.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Dashboard.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Init.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/ListServices.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Publish.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Register.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Remove.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Status.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/TestReachability.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Unpublish.java
---

## Decided
The user asked for direct CLI help. A command description is one short line that says what the command does, for example "Shows whether the Core Services work on this Workstation." It does not list the columns, states or parts that the command prints. A second line is kept only for behavior that a script needs, such as the exit code.

## Watch out
picocli shows only the first description line in the command list, and that list wraps at 80 columns. Keep the first line short enough to fit on one line.

In the code: the description of each @Command in cli/src/main/java/dev/palazon/lvhyard/cli/. Status and TestReachability keep an "Exits with 1 when ..." line because status --strict depends on it.
