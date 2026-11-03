---
title: "The API has one hostname per Service, where this Workstation routes it, and no Yard hostname"
author: Pablo
date: 2026-11-03
branch: init
cost: { minutes: 14, tokens: 28077 }
id: l-ebece2ef
scope: repo
status: active
replaces: l-a988af7a
src: agent
touches:
  - ".env.example"
  - cli/src/main/java/dev/palazon/lvhyard/cli/Init.java
  - cli/src/test/java/dev/palazon/lvhyard/cli/YardTest.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/Workstation.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Service.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/YardReader.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/PublicationsTest.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/ServicePageTest.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Publish.java
  - dashboard/openapi.yaml
---

## Decided
- A Service in the API has only `hostname`: where the Workstation you ask routes it. The user removed `yardHostname`. Each Workstation picks its own Base Domain, so on another Workstation it only repeated `hostname`, and on the Home Workstation it could only be a pattern.
- The Home Workstation still tells its user how teammates reach a Yard-scoped Service, as the pattern `<service>.<home>.<base-domain>`, with `<base-domain>` as literal text. The Service page and `lvh-yard publish --scope yard` build it from the Service's name and Workstation.

## Tried and ruled out
- A `yardHostname` ending with the Home Workstation's own Base Domain: that name goes nowhere on a Workstation with another Base Domain.
- Keeping `yardHostname` and filling it with the `<base-domain>` pattern: a field that is a real name on one Workstation and a pattern on another has two meanings, and any client can build the pattern itself.

## Watch out
- A name that holds `<base-domain>` is not reachable. Never send a request to it; Reachability tests use `hostname`.

In the code:
- Workstation.yardHostname(service) builds the pattern for the Service page; cli Publish builds the same one.
- dashboard/openapi.yaml no longer lists yardHostname.
