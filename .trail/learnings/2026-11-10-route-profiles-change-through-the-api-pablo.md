---
title: "Route Profiles change through the API, and a chain names its members only by name"
author: Pablo
date: 2026-11-10
branch: main
cost: { minutes: 6, tokens: 892 }
id: l-81a90198
scope: repo
status: active
replaces: l-f6040c14
src: agent
touches:
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/profiles/RouteProfiles.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/profiles/RouteProfileEditor.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/profiles/RouteProfileChecks.java
---

## Decided
- RouteProfileEditor is the one write path for Route Profiles, and RouteProfiles is only the read side, because one class with both makes a cycle with Publications.
- A Route Profile value in Consul KV is only its own middleware. A chain names its members, and every reader finds them by name in the same KV. The user chose this on 2026-11-05, because copies of members inside a chain gave stale chains.
- A member that KV lacks is left out, but the chain still names it, so Traefik refuses the Route (fail closed).
- A save writes only the changed values of the set, in one Consul transaction.
- A refused save answers 400 with a list of {profile, reason}. A remove of a profile in use answers 409 with its services and chains.

## Watch out
- Tests show "no write to a key" with its Consul ModifyIndex.
- Keep snakeyaml as a plain dependency in dashboard/pom.xml, because a test scope takes it away from Spring Boot, which reads application.yaml with it.

In the code: profiles/RouteProfileEditor.java, RouteProfiles.java, RouteProfileProblems.java, RouteProfilesThroughTraefikIT.
