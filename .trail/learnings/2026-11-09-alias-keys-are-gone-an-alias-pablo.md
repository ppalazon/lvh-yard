---
title: "Alias keys are gone: an Alias is stored in its Service record"
author: Pablo
date: 2026-11-09
branch: worktree-agent-a68043dbf4d606ee4
cost: { minutes: 32, tokens: 191920 }
id: l-30e417e4
scope: repo
status: active
replaces: l-a69549bc
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Aliases.java
---

## Decided
Nothing in the old learning holds now. The lvh-yard/aliases/<alias> keys, the /api/workstation/aliases API, the Aliases page, the alias_<alias> Traefik routers and the short Alias hostnames are gone. The `hidden` state is gone too.
An Alias is now an Instance Source of a local Service. It is stored in the Service record, as the learning "Aliases live in the Service record" says.

## Watch out
An Alias still gets its state from the Remote Service as this Workstation reads it. Any fault while reading the other Workstation makes the Alias unreachable, so it never breaks this Workstation's own pages.

In the code: dashboard/.../services/Aliases.java (alias, of), AliasTarget.java, ServiceRecords.addAlias.
