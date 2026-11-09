---
title: The Routing off flag tells off from no passing Instance
author: Pablo
date: 2026-11-09
branch: worktree-agent-a110ec1ee476e2e94
cost: { minutes: 8, tokens: 6227 }
id: l-140ef392
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Routing.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/TestReachability.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Dashboard.java
---

## Decided
Routing has an off field that says the Instance Priority is off. The API shows it only when true (NON_DEFAULT). Off and "no source passes" both give a Routing with no source, so the pages and the CLI need the flag to pick the right text.
lvh-yard test says that the Route uses every passing Instance, or every Instance in range if none passes, because the reachability answer has no Health. The Service page can tell the two apart through Routing.offWithPassingInstances.

## Tried and ruled out
Guessing off from the Health of the routed Instances: it hides a rule in a helper and still needs the order to be sure.

## Watch out
The CLI's Dashboard.Routing reads off as a Boolean, and isOff() turns null into false. A primitive boolean makes Jackson refuse every answer that leaves off out.

In the code: Routing.of and Routing.offWithPassingInstances, TestReachability.routingNow, Dashboard.Routing.isOff.
