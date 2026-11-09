---
title: "A Route Profile reaches Traefik as workstation_profile, with an underscore, so two Workstations never share a middleware name"
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 9, tokens: 79069 }
id: l-18d86b07
scope: repo
status: active
replaces: l-d877f0f8
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/routes/RouteBuilder.java
---

## Decided
Traefik gets each Route Profile as a middleware named <workstation>_<profile>. A Workstation name is a DNS label, so it never holds _, and the first _ always splits the name into its two parts.

## Tried and ruled out
The spec first said <workstation>-<profile>. The code review found a clash: Workstation a with profile b-c and Workstation a-b with profile c both gave a-b-c, and one middleware silently replaced the other. A dash can sit in both parts, so no dash rule can fix it.

## Watch out
Do not change the separator back to -.

In the code: RouteBuilder.middleware in routes/RouteBuilder.java. The clash test is routes/RouteBuilderTest.
