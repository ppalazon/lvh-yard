---
title: "The dashboard image builds the jar once for every CPU type, and stays off in compose unless its profile is on"
author: Pablo
date: 2026-11-02
branch: main
cost: { minutes: 11, tokens: 17728 }
id: l-55c8fe89
scope: repo
status: active
replaces: l-a3d75965
src: agent
touches:
  - compose.yaml
  - ".github/workflows/release.yml"
---

## Decided
- A multi-stage Dockerfile builds the image, not Spring Boot's build-image (buildpacks). Buildpacks do not build app.css, and they make amd64 + arm64 images harder.
- app.css and the jar are built once, on the builder's own platform. Only the last stage is per platform, and it has no RUN, so no QEMU is needed.
- The dashboard service in compose sits behind the `dashboard` profile, so `mise run up` and `dev:up` still start only Consul and Traefik.
- The user chose to pass each setting with its default written out, like the Consul service does, so compose.yaml shows every value the dashboard gets. The defaults must match application.yaml.
- YARD_OUT_OF_RANGE uses ${VAR-default}, not ${VAR:-default}: set but empty means nothing is out of range, and :- would turn empty into the default.
- YARD_WORKSTATION stays required (:?). Falling back to the machine's HOSTNAME was ruled out below.
- The version comes from the release tag through Maven's ${revision}.

## Tried and ruled out
- A toolchains.xml in the build container: not needed. The toolchain plugin finds the image's JDK through JAVA_HOME and selects it, despite useJdk=Never.
- HOSTNAME as the default Workstation name: bash does not export HOSTNAME, so Compose and `mise run dashboard` never see it, and Consul and the dashboard could get different names. Many machine names are also invalid (uppercase, dots) or not unique.

## Watch out
- A local multi-platform build fails with the default "docker" driver; use a docker-container builder.
- Exported shell variables beat .env, for Compose and for the dashboard alike.

In the code:
- compose.yaml: dashboard service, profiles: [dashboard], environment map with defaults
- Dockerfile: ./mvnw -q -B -pl dashboard package -DskipTests ${VERSION:+-Drevision=$VERSION}
- .github/workflows/release.yml: on tag v*, pushes ghcr.io/<owner>/lvh-yard-dashboard
