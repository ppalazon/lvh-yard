---
title: "No change to yard, consul-ui or traefik-admin is refused, and the API has no 409 for them"
author: Pablo
date: 2026-11-04
branch: main
cost: { minutes: 1, tokens: 5838 }
id: l-bbd88a96
scope: repo
status: active
replaces: l-4d7d1b6e
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Registrations.java
---

## Decided
- The 409 refusals that protected Core Services are gone (ADR 0006). The user can remove, unpublish, give Yard Scope, change the Instance Priority or add a Route Profile to yard, consul-ui and traefik-admin, as to any Service.
- The names yard and traefik are free. Only consul is refused, with 400.

## Watch out
- Changes to a Remote Service are still refused with 405. That rule is separate and stays.
- The CLI prints the detail of any refusal and exits 1, so it needs no code for a new refusal. Its stub tests pass only when openapi.yaml documents the answer.

In the code:
- CoreServiceException, Registrations.checkNotCore and Publications.keepCorePublication are gone
