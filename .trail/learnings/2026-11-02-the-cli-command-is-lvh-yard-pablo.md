---
title: "The CLI command is lvh-yard, not yard, while Yard stays the domain word"
author: Pablo
date: 2026-11-02
branch: main
cost: { minutes: 25, tokens: 57655 }
id: l-0b87319a
scope: repo
status: active
src: agent
touches:
  - cli/pom.xml
  - cli/src/main/java-templates/dev/palazon/lvhyard/cli/Version.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Yard.java
  - cli/src/test/java/dev/palazon/lvhyard/cli/YardTest.java
  - ".github/workflows/release.yml"
  - README.md
  - cli/src/main/java/dev/palazon/lvhyard/cli/NativeRecords.java
  - mise.toml
---

## Decided
- The user renamed the command from yard to lvh-yard: installed on a system PATH, yard is too generic a name. Older learnings that say "the yard executable" or "yard list" mean lvh-yard.
- Only the command's name changed: the executable, the release archives (lvh-yard-<version>-<platform>.tar.gz), the usage and version text, and the docs.
- Kept as they are: the domain word Yard (the yard Scope, the reserved name yard, yard.<base-domain>), the YARD_* settings such as YARD_DASHBOARD_URL, and the Java classes Yard and YardTest.

## Watch out
- .scratch/ issues still say `yard list` and so on: they record finished work and were left unchanged.

In the code:
- cli Yard.java: @Command(name = "lvh-yard"), version "lvh-yard <version>"; cli/pom.xml <imageName>lvh-yard</imageName>
