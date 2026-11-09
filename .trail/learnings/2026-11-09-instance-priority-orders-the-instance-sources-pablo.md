---
title: "Instance Priority orders the Instance Sources of a Service, and the dashboard picks the Route from Consul Health"
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 1, tokens: 2773 }
id: l-5f4b6632
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Routing.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ServiceRecords.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/InstanceSource.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/AliasTarget.java
---

## Decided
- The user wanted to order single sources (each Static Instance, Container Binding, Alias and Foreign Instance), not kinds, and a toggle that turns the order off (ADR-0009). The order and the toggle live in the Service record, not in the Publication, so unpublishing keeps them.
- Open question: when no source has a passing Instance, the Route uses every in-range Instance of every source, Alias Instances included. The user has not yet decided whether to keep that.

## Tried and ruled out
- An order of kinds (static, bound, alias), as in ADR-0005: it cannot choose between two sources of one kind.
- An order of single Instances: Bindings and Aliases give Instances that come and go.
- Weights: they spread requests, and they never stop sending requests to the Docker copy.
- Traefik's failover service: it switches only on Traefik's own HTTP checks, which repeat Consul's checks and cannot do TCP.
- Only appending a new ID to the stored order: a Foreign Instance that is not in the order yet then loses its place to the new source.
- Reachability tests run by another Workstation: the user dropped the feature.

## Watch out
- A switch takes up to about 15 s: one 10 s Consul check interval plus one 5 s Traefik poll.
- The Alias source ID is <workstation>/<service>. Parse and format it with AliasTarget, never by hand.
- ServiceRecords.sourceIds reads Consul directly, because a YardReader dependency makes a cycle through ContainerBindings.
- A Route to every Instance is also what you get before any check has run. A test that waits for a Route must first wait until its Instances pass, or it passes too early.

In the code:
- dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Routing.java (of), ServiceRecords.java (addSource, addAlias, sourceIds, ServiceRecord), InstanceSource.java (order), AliasTarget.java
- PagesController.instancePriority, InvalidPriorityException (400)
