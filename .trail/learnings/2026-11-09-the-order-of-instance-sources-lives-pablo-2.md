---
title: "The order of Instance Sources lives in the Service record, and new sources go to the end at write time"
author: Pablo
date: 2026-11-09
branch: worktree-agent-a68043dbf4d606ee4
cost: { minutes: 30, tokens: 191920 }
id: l-88cb2d7d
scope: repo
status: active
replaces: l-f1f4e7f7
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ServiceRecords.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/AliasTarget.java
---

## Decided
The Service record at lvh-yard/services/<svc> holds {"order": [source IDs], "priorityOff": true, "aliases": [IDs]}. An empty order, a toggle that is on and no Aliases are left out, so a new record is still {}. The Publication lost its priority, so unpublishing keeps the order and the toggle.

ServiceRecords.addSource runs after a Static Instance or a Container Binding is added, and addAlias after an Alias. They write the order as it reads now, sources the order does not name included, then the new ID. So a Foreign Instance that came first keeps its place before the new source. After LVH Yard removes a source, its ID leaves the order, so a re-added Binding goes to the end. ServiceRecords.sourceIds reads the source IDs straight from Consul: health, binding keys, and the aliases of the record. A YardReader dependency makes a cycle through ContainerBindings.

At read time, IDs that the order does not name go to the end, sorted by ID (InstanceSource.order). The sort makes every reader, and addSource, put them in the same place.

## Tried and ruled out
Only appending the new ID to the stored list: a Foreign Instance at the end loses its place to the new source.

## Watch out
The Alias source ID is <workstation>/<service> of its Remote Service. AliasTarget parses and formats it, so do not split it by hand.
When no source has a passing Instance, Routing.of uses every Instance in range of every source, Alias Instances included. The user has not yet decided whether to keep that.

In the code: dashboard/.../services/ServiceRecords.java (addSource, addAlias, sourceIds), InstanceSource.order, AliasTarget.java, Routing.of.
