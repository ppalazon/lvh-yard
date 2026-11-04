---
title: "Off is an empty Instance Priority, and the API Routing says off so the pages and the CLI can tell off from no passing Instance"
author: Pablo
date: 2026-11-04
branch: main
cost: { minutes: 5, tokens: 72449 }
id: l-050329a5
scope: repo
status: active
replaces: l-e34e4c63
src: agent
---

## Decided
- An empty order turns the Instance Priority off. The API, the page (blank priority field) and the CLI word `off` all send `[]`. It is stored as JSON, like any order other than the default.
- While off, the passing in-range Static and Bound Instances share the Route. If none passes, every in-range Instance is routed. A same-named Alias stays hidden, with the reason "whose Instance Priority is off."
- Routing has an `off` field. The API shows it only when true. Both off and "no kind passes" give a Routing with no kind, so the pages and the CLI need the flag to pick the right text.
- `lvh-yard test` says "The Instance Priority is off, so the Route uses every passing Instance, or every Instance in range if none passes." The reachability answer has no Health, so the CLI cannot tell which of the two applies. The page can, through Routing.offWithPassingInstances.
- "Use an order" on the page restores the default order, static, bound, alias.

## Tried and ruled out
- Guessing off from the Health of the routed Instances: it hides a rule in a helper and still needs the order to be sure.

## Watch out
- The CLI's Routing record reads `off` as a Boolean. A primitive boolean makes Jackson refuse every answer where `off` is left out.
- The CLI turns only the exact value `--priority off` into `[]`. `off,static` reaches the API as the unknown kind `off`.

In the code: Routing.of and Routing.offWithPassingInstances (dashboard services), ServicePage.routing and routingNow, Aliases.alias, Publish.run and TestReachability.routingNow in the CLI, Dashboard.Routing.isOff, InstancePriorityOffTest, YardTest.testSaysWhenTheInstancePriorityIsOff.
