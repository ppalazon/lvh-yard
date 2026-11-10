---
title: "The native yard executable registers Dashboard's records itself, and the CLI's tests run again against it"
author: Pablo
date: 2026-11-10
branch: main
cost: { minutes: 11, tokens: 193694 }
id: l-273e1da7
scope: repo
status: active
replaces: l-e03d4d34
src: agent
touches:
  - compose.yaml
  - cli/pom.xml
  - cli/src/main/java/dev/palazon/lvhyard/cli/NativeRecords.java
  - cli/src/test/java/dev/palazon/lvhyard/cli/YardTest.java
  - README.md
  - ".github/workflows/release.yml"
  - cli/src/main/java/dev/palazon/lvhyard/cli/Yard.java
---

## Decided
- A GraalVM Feature registers every record in Dashboard at build time, because a native image keeps no reflection that it is not told about, and a forgotten record in a hand-written list would fail only in the executable.
- The CLI tests run twice with -Pnative verify: on the JVM, then against the executable, so a reflection gap fails in CI, not on a user's computer.
- Linux executables are built on Ubuntu 22.04 for its older glibc, and each is packed in a tar.gz, because uploaded artifacts lose the executable bit.

## Watch out
- -DbuildArgs=... on the command line replaces the buildArgs of the pom, so the Feature is dropped and tests fail. Put extra native-image flags in the pom.
- picocli-codegen writes its files only when Maven compiles again, so build with clean after a change to commands.

In the code: cli/src/main/java/dev/palazon/lvhyard/cli/NativeRecords.java, cli/pom.xml (native profile, test-native), .github/workflows/release.yml.
