---
title: "The order of Instance Sources lives in the Service record, and new sources go to the end at write time"
author: Pablo
date: 2026-11-09
branch: worktree-agent-ae0f8e01ffe3b235a
cost: { minutes: 22, tokens: 149238 }
id: l-f1f4e7f7
scope: repo
status: active
replaces: l-eb0db14e
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ServiceRecords.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/InstanceSource.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Routing.java
---

## Decided
The Service record at lvh-yard/services/<svc> holds {"order": [source IDs], "priorityOff": true}. An empty order and a toggle that is on are left out, so a new record is still {}. The Publication lost its priority, so unpublishing keeps the order and the toggle.

ServiceRecords.addSource runs after a Static Instance, a Container Binding or an Alias is added. It writes the order as it reads now, sources the order does not name included, then the new ID. So a Foreign Instance that came first keeps its place before the new source. After LVH Yard removes a source, removeSource takes its ID out of the order, so a re-added Binding goes to the end. ServiceRecords reads the source IDs straight from Consul (health, binding keys, AliasKeys). A YardReader dependency makes a cycle through ContainerBindings.

At read time, IDs that the order does not name go to the end, sorted by ID (InstanceSource.order). The sort makes every reader, and addSource, put them in the same place.

When no source has a passing Instance, Routing.of uses every Instance in range of every source, Alias Instances included. The old rule left Alias Instances out of that last step.

## Tried and ruled out
Only appending the new ID to the stored list: a Foreign Instance at the end loses its place to the new source.

## Watch out
The Alias source ID is <workstation>/<service> of its Remote Service. ServiceRecords.sourceIds must read the Alias IDs from wherever Aliases are stored.
