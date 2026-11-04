---
title: "ws-remote's own route-profiles.yaml in tests"
author: Pablo
date: 2026-11-04
branch: worktree-agent-a245c802de4adb356
cost: { minutes: 9, tokens: 44364 }
id: l-f574b623
scope: repo
status: active
src: agent
touches:
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/RemoteWorkstation.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/routes/RouteBuilder.java
---

RemoteWorkstation.startDashboard always passes --lvh-yard.route-profiles.path=RemoteWorkstation.ROUTE_PROFILES, a temp file per JVM. Without it the ws-remote dashboard read the developer's real ~/.config/lvh-yard/route-profiles.yaml, because the default path in application.yaml points there. Tests give ws-remote profiles by writing that file, not by restarting it with whileDashboardRunsWith, which costs a restart. In RouteBuilder an Alias's Route reuses the middleware list of its target's Route (the applied map by backend name), so the Alias needs no profile lookup of its own.
