---
title: "Build the native yard executable with mise run native, which selects GraalVM for that task only"
author: Pablo
date: 2026-11-02
branch: main
cost: { minutes: 1, tokens: 10573 }
id: l-b7948c03
scope: repo
status: active
src: agent
touches:
  - mise.toml
---

## Decided
- `mise run native` selects GraalVM Community 25 for that task only and sets GRAALVM_HOME from its JAVA_HOME. The rest of the project keeps the system java.

## Tried and ruled out
- Plain ./mvnw -Pnative with GraalVM installed through mise: native-maven-plugin skips a GRAALVM_HOME that points nowhere, falls back to native-image on PATH, and finds mise's shim, which fails with "No version is set for shim: native-image" because no mise config selects GraalVM here.

## Watch out
- A stale GRAALVM_HOME in a shell profile (here /usr/lib/jvm/java-8-graalvm, which does not exist) sends the plugin to PATH without any warning.

In the code:
- mise.toml [tasks.native]: tools = { java = "graalvm-community-25.0.2" }, run = GRAALVM_HOME="$JAVA_HOME" ./mvnw -q -Pnative -pl cli clean verify
