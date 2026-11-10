---
title: "Instance Priority orders the Instance Sources of a Service, and the dashboard picks the Route from Consul Health"
author: Pablo
date: 2026-11-10
branch: main
cost: { minutes: 1, tokens: 2773 }
id: l-c21aaeba
scope: repo
status: active
replaces: l-5f4b6632
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Routing.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ServiceRecords.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/InstanceSource.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/AliasTarget.java
---

## Decided
- The user wanted to order single sources, not kinds, and a toggle that turns the order off (ADR-0009). The order and the toggle live in the Service record, so unpublishing keeps them.
- Open question: when no source has a passing Instance, the Route uses every in-range Instance of every source, Alias Instances included. The user has not yet decided whether to keep that.

## Tried and ruled out
- An order of kinds: it cannot choose between two sources of one kind.
- Weights: they spread requests, and never stop sending them to the Docker copy.
- Traefik's failover service: it switches only on Traefik's own HTTP checks, which cannot do TCP.
- Only appending a new ID: a Foreign Instance not yet in the order loses its place.

## Watch out
- A switch takes up to about 15 s: one 10 s check interval plus one 5 s Traefik poll.
- Parse and format an Alias source ID with AliasTarget, never by hand.
- ServiceRecords.sourceIds reads Consul directly, because a YardReader dependency makes a cycle.
- A test that waits for a Route must first wait until its Instances pass, because a Route to every Instance is also what you get before any check runs.

In the code: services/Routing.java, ServiceRecords.java, InstanceSource.java, AliasTarget.java.
