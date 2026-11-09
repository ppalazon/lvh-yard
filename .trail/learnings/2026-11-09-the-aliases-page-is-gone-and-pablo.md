---
title: "The Aliases page is gone, and green still marks every good state"
author: Pablo
date: 2026-11-09
branch: worktree-agent-a68043dbf4d606ee4
cost: { minutes: 30, tokens: 191920 }
id: l-f38c23d4
scope: repo
status: active
replaces: l-2659dced
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/InstanceSourcesCard.java
---

## Decided
The Aliases page and its state badges are gone. On the Service page an Alias is one Instance Source. An Alias that gives no Instances shows its reason as text (data-alias-reason), with no badge.
The rule from the old learning still holds: green (data-variant success) is the sign of every good state. The source the Route uses now gets the green "In use" badge (data-in-use).

## Watch out
For a new state that means "all is well", use the success badge. The user reads green as the sign of every good state.

In the code: dashboard/.../web/InstanceSourcesCard.java (render, aliasSource).
