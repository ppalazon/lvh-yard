---
title: "Instance Priority orders the attached Instance sources one by one, not Instance kinds, and a toggle turns it off"
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 1, tokens: 1716 }
id: l-eb0db14e
scope: repo
status: active
src: agent
touches:
  - docs/adr/0005-instance-priority-by-instance-kind.md
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/InstanceKind.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Routing.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/YardReader.java
---

## Decided
In the new Service workflow, the user asked that Instance Priority be an order of the concrete things attached to a Service: each Static Instance, each Container Binding and each Alias. The Route uses the first one in the order that is available. A toggle turns the order off, and then Traefik sends requests to any available Instance. So a Service can have many Aliases, many Static Instances and many Container Bindings.

This replaces the order by Instance kind (static, then bound, then alias) of ADR-0005.

In the same design, a Service is a record you create by name, and it stays while empty. An Alias belongs to a Service and has no hostname of its own. The Containers and Static Instances pages go away, and their forms move into the Service page. The container selector is a writable list, so you can type a container name or a Compose project/service that does not run now.

## Tried and ruled out
One Alias per Service with the order by kind: the user wants to order single sources, so it cannot hold.
Reachability tests run by another Workstation: the user dropped this feature.

## Watch out
A Container Binding or an Alias gives many Instances, so the order holds the attached sources, never single Instances.

In the code:
docs/adr/0005-instance-priority-by-instance-kind.md (to be replaced)
dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/InstanceKind.java
dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Routing.java
dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/YardReader.java (withAliases, service existence rule)
