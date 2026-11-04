---
title: "register static is a subcommand of register, and new Services publish by default"
author: Pablo
date: 2026-11-04
branch: worktree-agent-a93f069edacc6ccb9
cost: { minutes: 18, tokens: 45546 }
id: l-eeb9b479
scope: repo
status: active
src: agent
touches:
  - cli/src/main/java/dev/palazon/lvhyard/cli/Register.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Publications.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/DashboardTest.java
---

Watch out: picocli checks the required options and parameters of a parent command even when a subcommand is given, so lvh-yard register static web --port 8080 failed with exit 2 while Register still had a required <service> and --port. Register now has them optional (arity 0..1, Integer port) and asks for them in run() with a ParameterException, until ticket 05 removes the bare form. Decided: Publications.addPublishingNew(service, publish, add) holds the publish-a-new-Service rule (existence checked before the write, Local Scope, publish false opts out); the instances endpoint, the Static Instances page and ContainerImport use it, and the container-bindings endpoint can too. The API publishes when publish is left out, so the test helpers DashboardTest.register and RemoteWorkstation.register send publish false unless the test gives it, which keeps older tests' Services unpublished. The page form sends publish only when its checkbox is on, so a post without it leaves a new Service unpublished.
