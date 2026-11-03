---
title: "Off is an empty Instance Priority, and the Routing carries a hidden off flag so pages can tell off from no passing Instance"
author: Pablo
date: 2026-11-03
branch: main
cost: { minutes: 9, tokens: 147635 }
id: l-020ed54b
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/InstanceKind.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/InstancePriorityOffTest.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Routing.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Aliases.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/ServicePageTest.java
---

## Decided
- An empty order turns the Instance Priority off. The API, the page (blank priority field) and the CLI word `off` all send `[]`. It is stored as JSON, like any order other than the default.
- While off, the passing Static and Bound Instances share the Route. If none passes, every in-range Instance is routed. A same-named Alias stays hidden, with the reason "whose Instance Priority is off."
- Routing has a JSON-hidden `off` field. Both off and "no kind passes" give a Routing with no kind, and the Reachability result does not carry the order, so the pages need the flag to pick the right text.

## Tried and ruled out
- Guessing off from the Health of the routed Instances: it hides a rule in a helper and still needs the order to be sure.

## Watch out
- The CLI's `test` (reachability) output still says "No Instance passes" while off, because the API Routing has no off field. Adding one is a later API change.

In the code: Routing.of and Routing.passingWhileOff (dashboard services), ServicePage.routing and routingNow, Aliases.alias, Publish.run in the CLI, InstancePriorityOffTest.
