---
title: "CLI help and dashboard text give one short line, and a second line only for what a user or script must act on"
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 1, tokens: 1435 }
id: l-2f78fcd3
scope: repo
status: active
replaces: l-f281bbdf
src: agent
touches:
  - cli/src/main/java/dev/palazon/lvhyard/cli/Yard.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/Layout.java
---

## Decided
- The user asked for direct text. A command description, page intro, field help or result line says what the thing does, in one short sentence. It does not list the columns, states or parts that it shows, because the output already shows them, and a list in the text goes stale when they change.
- A second line stays only for behavior that a user or script must act on, such as the exit codes of lvh-yard status and lvh-yard test, because the output does not show an exit code.
- A field label that says enough gets no help text.

## Watch out
- picocli shows only the first description line in the command list, and wraps it at 80 columns. So the first line must make sense alone.

In the code: each @Command description in cli/src/main/java/dev/palazon/lvhyard/cli/, and the page text in dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/.
