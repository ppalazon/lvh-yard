---
title: FirstSetup finishes a half-written setup from lvh-yard/first-setup-names
author: Pablo
date: 2026-11-04
branch: worktree-agent-a66fabdd194654d10
cost: { minutes: 17, tokens: 41473 }
id: l-9543b690
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/FirstSetup.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/FirstSetupTest.java
---

decided: FirstSetup keeps the names it will write in Consul KV at FirstSetup.NAMES (lvh-yard/first-setup-names, comma-separated) before its first register. A later start that finds that key and no marker writes exactly those names again, even when they exist already, because a Service that existed before the setup is never on the list. The key starts with the marker's name, so the one values(null, MARKER) read gives both. The setup deletes the key, then writes the marker. A second register is safe: Registrations.register at the same host and port replaces the Instance and keeps its ID.
ruled out: an in-memory set of started names. It is lost when the dashboard stops between register and publish, and the next start then skips the half-written Service, which stays unpublished.
watch out: in FirstSetupTest the remote dashboard starts on a new random port each time, so yard moves. The half-written test keeps traefik-admin's Instance (fixed admin port); with yard it would get a second Instance.
