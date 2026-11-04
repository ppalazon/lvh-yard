---
title: The binding form sends the Kind enum by its name
author: Pablo
date: 2026-11-04
branch: worktree-agent-aa14a62b29111cd02
cost: { minutes: 9, tokens: 7331 }
id: l-635f8684
scope: repo
status: active
src: agent
touches:
  - "dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/ContainersPage.java#Kind"
  - "dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/PagesController.java#bindContainer"
---

## Decided
ContainersPage.Binding.kind is the enum Kind, and the radio buttons send CONTAINER or COMPOSE. The ids stay kind-container and kind-compose, because app.css hides the fields by #kind-compose.

## Ruled out
Lower-case values (container, compose) with an enum: Spring binds a form value to an enum only by its exact constant name, so the binding failed and the form answered 400 before PagesController.bindContainer ran. A converter for one form was more code than the enum saves.

## Decided
An empty Service name in the binding form takes ContainerBindingRequest.proposedService(): the cleaned container name, or the cleaned Compose service name, as the register endpoint and the CLI do.
