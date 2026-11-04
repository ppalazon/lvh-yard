---
title: "An active Alias is a good state, so it gets the green success badge too"
author: Pablo
date: 2026-11-04
branch: main
cost: { minutes: 1, tokens: 1475 }
id: l-2659dced
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/AliasesPage.java
---

## Decided
The active Alias badge is green, because the user asked for it after the spec. The spec listed passing Health, Healthy, OK and Reached as the good states, but not the active Alias. Every other Alias state keeps the outline badge, because those states need attention.

## Watch out
If you add a new state that means "all is well", give it the success badge. Otherwise it stays dark and does not match the other good states, because the user reads green as the sign of every good state.

In the code:
- dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/AliasesPage.java, state(): `alias.active() ? "success" : "outline"`
- AliasesPageTest checks `badge("success", "Active")` and `badge("outline", "Fallback")`.
