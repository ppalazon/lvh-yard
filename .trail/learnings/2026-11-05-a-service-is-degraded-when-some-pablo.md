---
title: "A Service is Degraded when some Instances pass and some do not, whatever the Instance Priority"
author: Pablo
date: 2026-11-05
branch: main
cost: { minutes: 1, tokens: 1695 }
id: l-fe6adb93
scope: repo
status: active
src: agent
touches:
  - ".scratch/catalog-per-service/spec.md"
  - GLOSSARY.md
  - dashboard/src/main/css/app.css
---

## Decided
- The user asked for one Catalog row per Service and a Degraded state. Degraded: published, at least one passing Instance, at least one that does not pass. Warning counts as not passing, as the Route skips warning Instances. Healthy now means every Instance passes.
- The state counts every Instance. It ignores the Instance Priority and the Out-of-range mark, so every Workstation shows the same state for a Service. The Service page already says what the Route uses.
- `lvh-yard list` stays one line per Instance, because `lvh-yard remove --id` needs the IDs it prints. A new `lvh-yard catalog` gives the one-line-per-Service view, with no Service Warnings.
- The detail for each container goes into new columns of the Instances card, not a second table. Container Bindings rows show a summary.

## Tried and ruled out
- A state that counts only the Instances of the kind the Route uses: a failing Bound Instance behind a passing Static Instance would then hide as Healthy.
- The word "exposed port": in Docker it means an image's EXPOSE line, not a mapped port. The terms are Container Port and Host Port.
- An amber Degraded badge: the buttons are amber. It uses the existing orange risk color.

## Watch out
- A host-network container has no port mapping, but its Bound Instance is at the Workstation address and the Container Port. So "no mapped port" does not mean "Not published" for the Host Port.

In the code:
- .scratch/catalog-per-service/spec.md and issues/01 to 04
- GLOSSARY.md: Degraded, Healthy, Container Port, Host Port
- ServiceState.of, Routing.passing, the --risk color in dashboard/src/main/css/app.css
