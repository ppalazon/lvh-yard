---
title: "The API has one hostname per Service, where this Workstation routes it, and no Yard hostname"
author: Pablo
date: 2026-11-10
branch: main
cost: { minutes: 14, tokens: 28077 }
id: l-c849b14d
scope: repo
status: active
replaces: l-ebece2ef
src: agent
touches:
  - ".env.example"
  - cli/src/main/java/dev/palazon/lvhyard/cli/Init.java
  - cli/src/test/java/dev/palazon/lvhyard/cli/YardTest.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/Workstation.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Service.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/YardReader.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/PublicationsComponentTest.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/ServicePageComponentTest.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Publish.java
  - dashboard/openapi.yaml
---

## Decided
- A Service in the API has only hostname: where the Workstation that you ask routes it. The user removed yardHostname, because each Workstation picks its own Base Domain. On another Workstation it only repeated hostname, and on the Home Workstation it could only be a pattern.
- The Home Workstation tells its user how teammates reach a Yard-scoped Service, as the pattern <service>.<home>.<base-domain>, with <base-domain> as literal text.

## Tried and ruled out
- A yardHostname with the Home Workstation's own Base Domain: that name goes nowhere on a Workstation with another Base Domain.
- A yardHostname that holds the pattern: a field that is a real name on one Workstation and a pattern on another has two meanings.

## Watch out
- A name that holds <base-domain> is not reachable. Reachability tests use hostname.

In the code: Workstation.yardHostname(service) builds the pattern for the Service page, cli Publish.java builds the same one.
