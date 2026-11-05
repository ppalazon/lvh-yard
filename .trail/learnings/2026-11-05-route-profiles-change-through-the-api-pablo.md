---
title: "Route Profiles change through the API, and chains are written again when a member changes"
author: Pablo
date: 2026-11-05
branch: worktree-agent-ac8b99a269d7173c4
cost: { minutes: 11, tokens: 40598 }
id: l-ef7dc962
scope: repo
status: active
replaces: l-ff325647
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/consul/Consul.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/RouteProfilesTest.java
---

## Decided
- RouteProfileEditor is the one write path for Route Profiles (list, find, save, remove). RouteProfiles is only the read side, because Publications needs RouteProfiles.names() and the editor needs Publications.servicesByProfile() for the "used by" list and the remove conflict. One class with both makes a dependency cycle.
- RouteProfileEditor.save writes again every stored chain that is not in the set, through RouteProfileChecks.check(chains, after). A chain's KV value copies its members. The Routes can take a member from the chain's old copy instead of the member's new value. Only values that changed are written, in one Consul transaction (Consul.putValues, /v1/txn, 64 writes each).
- The Route Profiles API has its own advice, RouteProfileProblems, because ApiProblems is package-private in services and lists its controllers by class. A refused save answers 400 with a "problems" list of {profile, reason}. A remove in use answers 409 with "services" and "chains".
- The Traefik part of the status lost its "route profiles" detail: with no file there is no broken state to show.

## Watch out
- Dashboard tests share one Consul, and Publications of earlier tests stay. A test that checks the "services" of a profile must use a profile name that no other test publishes.
- Tests clean up with removeAllRouteProfiles() (consul kv delete -recurse), not the API, because the API refuses to remove a profile that a leftover Publication names.
- Keep snakeyaml as a plain dependency in dashboard/pom.xml: a test scope takes it away from Spring Boot, which reads application.yaml with it.
