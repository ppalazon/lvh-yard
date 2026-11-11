---
title: "Out-of-range Instance is the term, never Unreachable Instance"
author: Pablo
date: 2026-10-30
id: l-2a382d5b
scope: repo
status: active
src: init-sessions
---

## Decided
- Unreachable already means a whole Workstation cannot be read, and the glossary has Reachability too. The user asked for a different name for Instances that only their own Workstation can reach.
- In the code: Use outOfRange in the API and the Out-of-range Instance glossary term. The remaining `unreachable` fields are for Workstations only. The glossary lists Unreachable Instance as a word to avoid.

_From past Claude sessions here, Oct 30._
