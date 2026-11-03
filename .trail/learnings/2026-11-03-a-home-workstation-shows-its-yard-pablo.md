---
title: "A Home Workstation shows its Yard hostname with <base-domain> left as written"
author: Pablo
date: 2026-11-03
branch: init
cost: { minutes: 5, tokens: 24282 }
id: l-a988af7a
scope: repo
status: active
src: agent
touches:
  - GLOSSARY.md
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/PublicationsTest.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/Workstation.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/YardReader.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Service.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/ServicePageTest.java
---

## Decided
- On its Home Workstation, a Yard-scoped Service's Yard hostname is shown as `<service>.<home>.<base-domain>`, with `<base-domain>` as literal text. The user chose this: the hostname depends on who looks, because each Workstation picks its own Base Domain, and the Home Workstation cannot know the others'.
- Seen from any other Workstation, the Yard hostname stays a real name with that Workstation's own Base Domain, because that is its own Route.

## Tried and ruled out
- Ending it with the Home Workstation's own Base Domain: that name goes nowhere on a Workstation that uses another Base Domain.
- Showing only a Workstation's own hostnames, with no Yard hostname: the user preferred telling people the pattern.

## Watch out
- A hostname that holds `<base-domain>` is not reachable. Never send a request to it, for example in a Reachability test, which uses `hostname`, not `yardHostname`.

In the code:
- Workstation.yardHostname(service) and Workstation.VIEWERS_BASE_DOMAIN; YardReader.yardHostname picks it when the Service is this Workstation's own.
- The API field is `yardHostname`; the Service page and `lvh-yard publish --scope yard` print it.
