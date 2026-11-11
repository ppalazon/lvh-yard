---
title: The Routing off flag tells off from no passing Instance
author: Pablo
date: 2026-11-10
branch: go-cli-ticket-17
id: l-41c4ae1c
scope: repo
status: active
replaces: l-140ef392
src: agent
touches:
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/Routing.java
  - cli/internal/command/reachability.go
---

## Decided
Routing has an off field that says the Instance Priority is off. The API shows it only when true (NON_DEFAULT). Off and "no source passes" both give a Routing with no source, so the pages and the CLI need the flag to pick the right text.
lvh-yard test says that the Route uses every passing Instance, or every Instance in range if none passes, because the reachability answer has no Health. The Service page can tell the two apart through Routing.offWithPassingInstances.

## Tried and ruled out
Guessing off from the Health of the routed Instances: it hides a rule in a helper and still needs the order to be sure.

## Watch out
The API leaves a false off out, so the CLI reads a missing off as false (client.Routing.Off is omitempty).

In the code: Routing.of and Routing.offWithPassingInstances, reachabilityRouting in cli/internal/command/reachability.go.
