---
title: "Unit tests end in Test, component tests in ComponentTest, integration tests in IT"
author: Pablo
date: 2026-11-09
branch: worktree-agent-a68043dbf4d606ee4
cost: { minutes: 37, tokens: 232785 }
id: l-2c466c3a
scope: repo
status: active
replaces: l-2118a3a2
src: agent
touches:
  - mise.toml
---

## Decided
The user asked for fast unit tests next to the slow tests (docs/adr/0008). A unit test starts no Spring context, no Docker container and no process. Edge cases of a rule go to unit tests. The dashboard's component tests (*ComponentTest) run the dashboard with fake Consul and Docker. Each feature keeps one or a few IT tests for the wiring. mise run test:unit runs the unit tests of both modules in a few seconds. mise run check runs everything.

## Tried and ruled out
Running only the tests of the changed package: the tests go through the REST API, so a change in one package breaks tests in others.

## Watch out
To run one integration test, use -Dit.test=<Class>#<method> with the Maven phase "verify", and -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false to skip the unit tests.
mise run openapi no longer does this. It runs OpenApiComponentTest#committedCopyIsCurrent with surefire and -Dopenapi.update=true, so it needs no Docker.
An unknown Scope in a Publication request is refused by Jackson while it reads the body.

In the code: dashboard/pom.xml and cli/pom.xml (maven-failsafe-plugin), mise.toml (test:unit, test:component, openapi), OpenApiComponentTest.
