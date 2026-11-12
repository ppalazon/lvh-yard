---
title: "The dashboard image builds the jar once for every CPU type, and stays off in compose unless its profile is on"
author: Pablo
date: 2026-11-10
branch: main
cost: { minutes: 11, tokens: 17728 }
id: l-1f24b655
scope: repo
status: active
replaces: l-55c8fe89
src: agent
touches:
  - config/compose.yaml
  - ".github/workflows/release.yml"
---

## Decided
- A multi-stage Dockerfile builds the image, not Spring Boot's buildpacks, because buildpacks do not build app.css and make amd64 plus arm64 images harder. app.css and the jar are built once. Only the last stage is per platform, and it has no RUN, so no QEMU is needed.
- The dashboard service in config/compose.yaml sits behind the dashboard profile. dev/workstations.sh starts only Consul and Traefik, because the test dashboards run from sources.
- config/compose.yaml passes each setting with its default written out, so it shows every value the dashboard gets. The defaults must match application.yaml.
- YARD_OUT_OF_RANGE uses ${VAR-default}, not ${VAR:-default}, because set but empty must mean that nothing is out of range.

## Tried and ruled out
- HOSTNAME as the default Workstation name: bash does not export it, so Consul and the dashboard could get different names.

## Watch out
- A local multi-platform build fails with the default docker driver. Use a docker-container builder.
- Exported shell variables beat .env, for Compose and the dashboard alike.

In the code: Dockerfile, config/compose.yaml (dashboard service), .github/workflows/release.yml.
