---
title: "CLI help and dashboard text give one short line, and a second line only for what a user or script must act on"
author: Pablo
date: 2026-11-10
branch: go-cli-ticket-17
id: l-c2a01aa2
scope: repo
status: active
replaces: l-2f78fcd3
src: agent
touches:
  - cli/internal/command/root.go
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/Layout.java
---

## Decided
- The user asked for direct text. A command description, page intro, field help or result line says what the thing does, in one short sentence. It does not list the columns, states or parts that it shows, because the output already shows them, and a list in the text goes stale when they change.
- A second line stays only for behavior that a user or script must act on, such as the exit codes of lvh-yard status and lvh-yard test, because the output does not show an exit code.
- A field label that says enough gets no help text.

## Watch out
- Cobra shows only Short in the command list. So Short must make sense alone.

In the code: the Short text of each command in cli/internal/command/, and the page text in dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/.
