---
title: Container registration rules that outlived the Containers page
author: Pablo
date: 2026-11-09
branch: worktree-agent-a110ec1ee476e2e94
cost: { minutes: 8, tokens: 6227 }
id: l-2f604883
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ApiProblems.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/docker/DockerFailures.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/RegisterContainer.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Register.java
  - dashboard/src/main/css/app.css
---

## Decided
A container name that no running container has is a container that does not run. Registering or binding it is never a 404: the Binding is stored by name, and lvh-yard register container says that the Service gets its Bound Instance once one starts.
ApiProblems adds a field property, from InvalidRegistrationException.field(), to every 400 of a registration, so a form and the CLI can name the field.
Only the proposed default Service name is cleaned (ListedContainer.proposedService). A name the user types is checked as typed, so my_app is still refused.
The Docker failure text comes from DockerFailures, which DockerStatus and ContainerList share, so the status page and the bind form say the same thing.
Basecoat 1.1.0 and Tailwind have no success badge. app.css adds its own .badge[data-variant=success].

## Watch out
Docker tests assert only on their own containers, never on the whole list, because other JVMs and the developer share the same Docker.
picocli checks the required options and parameters of a parent command even when a subcommand runs. Keep Register itself free of required options, or register static and register container fail with exit 2.

In the code: ApiProblems, DockerFailures, ListedContainer.proposedService, RegisterContainer, Register, dashboard/src/main/css/app.css.
