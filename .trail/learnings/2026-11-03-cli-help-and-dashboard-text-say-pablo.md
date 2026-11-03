---
title: "CLI help and dashboard text say only what a thing does, and never explain what it shows"
author: Pablo
date: 2026-11-03
branch: main
cost: { minutes: 11, tokens: 27685 }
id: l-f281bbdf
scope: repo
status: active
replaces: l-d3493ba5
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
The user asked for direct text in the CLI help and on the dashboard pages. A command description, page intro, field help or result line says what the thing is or does, in one short sentence. For example: "Shows whether the Core Services work on this Workstation." It does not list the columns, states or parts that it shows, and it does not explain how the system behaves around it. A field label that says enough gets no help text.

## Watch out
A second line stays only for behavior that a user or script needs to act, such as the exit code of lvh-yard status and lvh-yard test. picocli shows only the first description line in the command list, and wraps it at 80 columns.

API error messages, OpenAPI descriptions and code comments were not part of this change.

In the code: each @Command description in cli/src/main/java/dev/palazon/lvhyard/cli/, and the page text in dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/.
