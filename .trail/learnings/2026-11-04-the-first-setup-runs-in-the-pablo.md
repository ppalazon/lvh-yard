---
title: "The first setup runs in the background with a time limit, and the marker key says it is done"
author: Pablo
date: 2026-11-04
branch: main
cost: { minutes: 1, tokens: 5838 }
id: l-919b87a5
scope: repo
status: active
replaces: l-93add257
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/FirstSetup.java
---

## Decided
- Core Services are gone (ADR 0006). The first setup took over the background run: it never holds up the dashboard's start. Each try has a 10 s limit, and a failed or late try is tried again every 5 s.
- The setup writes the time at a marker key when it is done. When the marker exists, the setup does nothing, also after a restart.
- The old fixed Instance IDs and the core meta are gone. The setup writes ordinary Static Instances.

## Tried and ruled out
- Proving "a second start changes nothing" by a check's output or Health: Consul keeps a check's status and output when the same registration is sent again. Tests check IDs and no duplicate Instances instead.

## Watch out
- Tests cannot assume the three Services exist when the context is up. The test seam waits for the marker key.
- The test Consul runs checks inside its container, so checks on 127.0.0.1 are critical in tests. Test that checks exist, not that they pass.

In the code:
- Marker key lvh-yard/first-setup (FirstSetup.MARKER), FirstSetup.TRY_LIMIT and RETRY
- The old key lvh-yard/core-services/initialized is gone
