---
title: "An optimized lvh-yard build uses -O3, because it is both faster and smaller than the default -O2"
author: Pablo
date: 2026-11-04
branch: main
cost: { minutes: 4, tokens: 49020 }
id: l-7cea2080
scope: repo
status: active
src: agent
touches:
  - docs/development.md
  - cli/pom.xml
  - mise.toml
---

## Decided
- The user asked for a task that makes the executable smaller and faster. Measured on GraalVM Community 25.0.2, Linux amd64: -O2 (default) 51 MB, 17.4 MB gzip, --help 7.9 ms; -O3 49 MB, 16.5 MB gzip, 7.3 ms; -Os 40 MB, 14.1 MB gzip, 9.7 ms.
- So the optimize task builds with -O3 by default, and -Os only on request (--for size). -O3 wins on both counts, against what one expects.
- The copy task does not build first: a native build takes about 40 s with tests, and a copy often repeats.

## Tried and ruled out
- strip: the executable is already stripped.
- -march=native and PGO: -march=native makes an executable that can fail on another computer's CPU, and PGO needs Oracle GraalVM, not Community.

## Watch out
- The copy writes lvh-yard.new and then moves it, because a running lvh-yard cannot be overwritten in place ("Text file busy").

In the code:
- cli/pom.xml property native.optimization (default 2), buildArg -O${native.optimization}
- mise.toml tasks native:optimize (--for speed|size) and native:copy (<host>, --dir default .local/bin)
