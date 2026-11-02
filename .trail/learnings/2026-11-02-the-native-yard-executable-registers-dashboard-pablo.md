---
title: "The native yard executable registers Dashboard's records itself, and the CLI's tests run again against it"
author: Pablo
date: 2026-11-02
branch: main
cost: { minutes: 11, tokens: 193694 }
id: l-e03d4d34
scope: repo
status: active
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
- Jackson reads the API's JSON into records nested in Dashboard. A native image keeps no reflection it is not told about, so a GraalVM Feature registers every record in Dashboard at build time. A new record needs nothing; a hand-written list was ruled out because a forgotten record would fail only in the executable.
- picocli-codegen (annotation processor) lists the commands' options; Jackson 3's and picocli's own metadata come from GraalVM's reachability metadata repository through the Spring Boot parent's native profile.
- The CLI's tests run twice with -Pnative verify: on the JVM, then against the executable (system property yard.executable), so a reflection gap fails in CI, not on a user's machine.
- Linux executables are built on Ubuntu 22.04 for its older glibc; each is packed in a tar.gz because uploaded artifacts lose the executable bit.

## Tried and ruled out
- -Os (optimize for size): 50 MB down to 40 MB, but the tar.gz download is about 17 MB either way.

## Watch out
- -DbuildArgs=... on the command line replaces the pom's buildArgs, so the Feature is dropped and 35 tests fail. Put extra native-image flags in the pom.
- picocli-codegen writes its files only when Maven recompiles: build with clean after changing commands.
- A native build of only --help is small (16 MB) and misleading: Jackson and the HTTP client are not reached until records are registered.

In the code:
- cli/src/main/java/dev/palazon/lvhyard/cli/NativeRecords.java, --features=dev.palazon.lvhyard.cli.NativeRecords in cli/pom.xml's native profile
- YardTest.NATIVE_YARD; surefire execution test-native in the integration-test phase
- .github/workflows/release.yml jobs cli-native and release
