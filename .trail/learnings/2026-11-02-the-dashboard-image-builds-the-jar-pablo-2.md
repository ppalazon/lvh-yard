---
title: "The dashboard image builds the jar once for every CPU type, and stays off in compose unless its profile is on"
author: Pablo
date: 2026-11-02
branch: chore/pre-commit-formatting
cost: { minutes: 1, tokens: 3792 }
id: l-a3d75965
scope: repo
status: active
src: agent
touches:
  - Dockerfile
  - compose.yaml
  - ".github/workflows/release.yml"
---

## Decided
- A multi-stage Dockerfile builds the image, not Spring Boot's build-image (buildpacks). Buildpacks do not build app.css, and they make amd64 + arm64 images harder.
- app.css and the jar are built once, on the builder's own platform. Only the last stage is per platform, and it has no RUN, so no QEMU is needed.
- The dashboard service in compose sits behind the `dashboard` profile, so `mise run up` and `dev:up` still start only Consul and Traefik.
- Settings pass to the container by name only, never as ${VAR:-default}: a YARD_OUT_OF_RANGE set but empty must stay empty.
- The version comes from the release tag through Maven's ${revision}.

## Tried and ruled out
- A toolchains.xml in the build container: not needed. The toolchain plugin finds the image's JDK through JAVA_HOME (/opt/java/openjdk in eclipse-temurin) and selects it, despite useJdk=Never.

## Watch out
- A local multi-platform build fails with the default "docker" driver; use a docker-container builder.

In the code:
- Dockerfile: ./mvnw -q -B -pl dashboard package -DskipTests ${VERSION:+-Drevision=$VERSION}
- compose.yaml: dashboard service, profiles: [dashboard]
- .github/workflows/release.yml: on tag v*, pushes ghcr.io/<owner>/lvh-yard-dashboard
