---
title: "Dashboard tests log only warnings, and test settings go in the Maven test plugins, not in a test application.yaml"
author: Pablo
date: 2026-11-09
branch: main
cost: { minutes: 20, tokens: 257902 }
id: l-3dcbc921
scope: repo
status: active
src: agent
touches:
  - docs/user-guide.md
  - ".scratch/navigation-and-register/spec.md"
  - docs/adr/0001-one-consul-datacenter-per-workstation.md
  - docs/adr/0002-traefik-reads-routes-from-lvh-yard.md
  - docs/adr/0003-hostnames-name-the-home-workstation.md
  - docs/adr/0004-route-profiles-travel-through-consul-kv.md
  - docs/adr/0006-lvh-yard-sets-up-its-own-services-once.md
  - docs/adr/0007-reactive-dashboard-with-sse-and-htmx.md
  - docs/adr/0008-unit-component-and-integration-tests.md
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/CreateAndDeleteServicesComponentTest.java
---

## Decided
- The user wanted a passing check to print little, because an agent reads all of its output. A test-only Logback file sets the root logger to WARN. A passing mise run check went from 1,412 lines to 88.
- Two library loggers are at ERROR, because every test JVM repeats their warning: SpringDoc's "api-docs is enabled" and SpringBootTestContextBootstrapper's "no @Configuration".
- The Spring banner is off through a system property in Surefire and Failsafe.

## Tried and ruled out
- A test application.yaml for the banner: a file of that name on the test classpath hides the main application.yaml, so the dashboard would lose its own settings in tests.

## Watch out
- The warnings that are left come from tests that cause them on purpose, such as a missing Docker socket or an unknown Scope in ws-remote. They do not mean that a test failed.
- No test reads log output. If a test starts to read it, it must raise its logger's level itself.

In the code:
- dashboard/src/test/resources/logback-test.xml
- dashboard/pom.xml: systemPropertyVariables spring.main.banner-mode=off in maven-surefire-plugin and maven-failsafe-plugin
- Full output: dashboard/target/surefire-reports/ and dashboard/target/failsafe-reports/
