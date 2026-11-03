---
title: "init --upgrade edits only image lines, and the init modes share one picocli group"
author: Pablo
date: 2026-11-03
branch: init-03
cost: { minutes: 9, tokens: 49542 }
id: l-d25e47ed
scope: repo
status: active
src: agent
touches:
  - cli/src/main/java/dev/palazon/lvhyard/cli/Init.java
---

## Decided
- `lvh-yard init --upgrade` reads compose.yaml with ComposeImages, which finds each service's first `image:` line by line (a two-space service header under the top-level `services:`) and swaps only the image value, keeping indent, quotes and trailing comment. It never reparses the YAML, so hand edits stay byte for byte.
- The new images come from ComposeImages.parse(Templates.compose()), so --upgrade moves to exactly what a fresh init would write; no versions are listed twice.
- When consul, traefik or dashboard has no `image:` line, nothing is written and the exit is 1, because a half-upgraded file is harder to read than an untouched one.
- --force, --upgrade and --reinit live in Init.Mode, a picocli @ArgGroup(exclusive = true): picocli itself refuses two of them with exit 2, naming only the two given ("--force, --upgrade are mutually exclusive"). A new mode is one more @Option in Mode, read as mode.<name>.

## Watch out
- Init.mode is initialised to new Mode() so mode.force is safe to read when no option of the group is given.
