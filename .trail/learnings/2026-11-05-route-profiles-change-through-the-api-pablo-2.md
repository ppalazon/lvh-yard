---
title: "Route Profiles change through the API, and a chain names its members only by name"
author: Pablo
date: 2026-11-05
branch: worktree-agent-a7585303341c994bf
cost: { minutes: 6, tokens: 892 }
id: l-f6040c14
scope: repo
status: active
replaces: l-ef7dc962
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/profiles/RouteProfiles.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/profiles/RouteProfileEditor.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/profiles/RouteProfileChecks.java
---

## Decided
- RouteProfileEditor is the one write path for Route Profiles (list, find, save, remove). RouteProfiles is only the read side, because Publications needs RouteProfiles.names() and the editor needs Publications.servicesByProfile() for the "used by" list and the remove conflict. One class with both makes a dependency cycle.
- A Route Profile value in Consul KV is only its own middleware, the bare Traefik object, such as {"chain": {"middlewares": ["office-only", "login"]}}. The user chose this on 2026-11-05: Traefik names chain members, and every reader (RouteProfiles.stored(datacenter) in YardReader) already reads all profiles of a Workstation. Copies of members inside a chain value only caused stale chains, and save had to rewrite every chain that reached a changed member.
- RouteProfiles.stored resolves chain members by name into RouteProfileDefinition.middlewares, so RouteBuilder and asksWhoTheUserIs did not change. A member that KV lacks is left out, but the chain still names it, so Traefik refuses the Route (fail closed; RouteProfilesThroughTraefikTest shows a 404).
- RouteProfileEditor.save writes only the set, and only values that changed, in one Consul transaction. A broken stored chain no longer blocks other saves.
- The Route Profiles API has its own advice, RouteProfileProblems, because ApiProblems is package-private in services and lists its controllers by class. A refused save answers 400 with a "problems" list of {profile, reason}. A remove in use answers 409 with "services" and "chains".

## Ruled out
- Compatibility code for values with copies: the app is not released, so no such values exist.

## Watch out
- Dashboard tests share one Consul, and Publications of earlier tests stay. A test that checks the "services" of a profile must use a profile name that no other test publishes.
- Tests clean up with removeAllRouteProfiles() (consul kv delete -recurse), not the API, because the API refuses to remove a profile that a leftover Publication names.
- Tests show "no write to a key" with its Consul ModifyIndex (consul kv get -detailed, or RemoteWorkstation.consulModifyIndex).
- Keep snakeyaml as a plain dependency in dashboard/pom.xml: a test scope takes it away from Spring Boot, which reads application.yaml with it.
