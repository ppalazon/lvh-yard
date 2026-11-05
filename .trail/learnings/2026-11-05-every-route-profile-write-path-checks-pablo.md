---
title: Every Route Profile write path checks with RouteProfileChecks
author: Pablo
date: 2026-11-05
branch: worktree-agent-a8d5f78ba4c966513
cost: { minutes: 6, tokens: 33692 }
id: l-ff325647
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/profiles/RouteProfileChecks.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/profiles/RouteProfileReader.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/RouteProfileChecksTest.java
---

## Decided
- RouteProfileChecks.check(set, stored) is the one place that checks Route Profiles. stored is RouteProfiles.stored(null), so a chain may name a profile already in Consul KV. A profile of the set replaces the stored one of the same name.
- Problem.unknownType exists only because route-profiles.yaml breaks the whole file on an unknown type, as a typo would otherwise open the Services that use the profile. Later write paths refuse the whole set on any problem, so they can ignore it. It goes with the file reader.
- The file reader passes no stored profiles: the file holds them all, and refresh deletes KV keys that the file does not name.

## Watch out
- RouteProfileChecksTest calls the bean from the running dashboard, because no API takes a set yet. Once the PUT endpoints exist, move these tests to the API.
- A stored chain's KV value copies its members. When a member changes, the chain's value keeps the old copy unless a write path writes the chain again.
