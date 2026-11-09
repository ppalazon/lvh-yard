---
title: "A Service is Degraded when some Instances pass and some do not, whatever the Instance Priority"
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 3, tokens: 24779 }
id: l-b6295b2b
scope: repo
status: active
replaces: l-fe6adb93
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ServiceState.java
---

## Decided
- Degraded: published, at least one passing Instance and at least one that does not pass. Warning counts as not passing, because the Route skips warning Instances.
- The state counts every Instance. It ignores the Instance Priority and the Out-of-range mark, so every Workstation shows the same state for a Service.
## Tried and ruled out
- A state that counts only the Instances that the Route uses: a failing Bound Instance behind a passing Static Instance would then hide as Healthy.
- The word "exposed port": in Docker it means the EXPOSE line of an image. The terms are Container Port and Host Port.
- An amber Degraded badge: the buttons are amber, so it uses the orange risk color.

In the code: ServiceState.of, the --risk color in dashboard/src/main/css/app.css.
