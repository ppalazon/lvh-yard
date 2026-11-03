---
title: lvh-yard init asks for the Workstation and peers at a terminal
author: Pablo
date: 2026-11-03
branch: init-02
cost: { minutes: 6, tokens: 45698 }
id: l-67e19575
scope: repo
status: active
src: agent
touches:
  - cli/src/main/java/dev/palazon/lvhyard/cli/Init.java
  - cli/src/test/java/dev/palazon/lvhyard/cli/YardTest.java
---

## Decided
- Init asks only for --workstation and --peers. The gossip key and base domain have good defaults, so asking for them would only slow a new user down.
- Init.ask() reads plain lines from System.in through one BufferedReader kept for the whole run. A second reader would lose the answers the first one buffered. A real terminal is System.in too, so tests and people go through the same code.
- A hidden --terminal option makes Init act as if a terminal were attached. YardTest.yardAtTerminal() adds it and types the answers on the process's stdin. System.console() cannot be faked from a test, in the JVM or in the native executable.
- --peers has no default (null), so init can tell "not given" from "given empty". Without a terminal, missing peers are simply none; only a missing --workstation is a usage error.
- When the input ends before an answer, init fails with exit 2 naming the flag, so a pipe can never make it loop.
