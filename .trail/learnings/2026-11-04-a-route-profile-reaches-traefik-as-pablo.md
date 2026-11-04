---
title: "A Route Profile reaches Traefik as workstation_profile, with an underscore, so two Workstations never share a middleware name"
author: Pablo
date: 2026-11-04
branch: main
cost: { minutes: 29, tokens: 74693 }
id: l-d877f0f8
scope: repo
status: active
src: agent
touches:
  - ".scratch/route-profiles/issues/01-read-and-check-the-profiles-file.md"
  - ".scratch/route-profiles/issues/02-apply-profiles-on-home-routes.md"
  - ".scratch/route-profiles/issues/03-profiles-travel-to-every-workstation.md"
  - ".scratch/route-profiles/issues/04-broken-file-and-missing-profile.md"
  - ".scratch/route-profiles/spec.md"
  - cli/src/main/java/dev/palazon/lvhyard/cli/Profiles.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Publish.java
  - dashboard/openapi.yaml
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/profiles/RouteProfileReader.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/profiles/RouteProfiles.java
---

## Decided
Traefik gets each Route Profile as a middleware named `<workstation>_<profile>`. A Workstation name is a DNS label, so it never holds `_`. The first `_` always splits the name into its two parts.

## Tried and ruled out
The spec first said `<workstation>-<profile>`. The code review found a clash: Workstation `a` with profile `b-c` and Workstation `a-b` with profile `c` both gave `a-b-c`, and one middleware silently replaced the other. A dash can sit in both parts, so no dash rule can fix it.

## Watch out
Middlewares and routers are separate Traefik name spaces, so the `alias_` router prefix does not clash with these names. Do not change the separator back to `-`.

In the code: RouteBuilder.middleware in dashboard/.../routes/RouteBuilder.java; the clash test is routes/RouteBuilderTest.
