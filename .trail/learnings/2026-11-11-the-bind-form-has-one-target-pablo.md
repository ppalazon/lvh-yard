---
title: "The bind form has one target field, and a small script makes it a list you can type in"
author: Pablo
date: 2026-11-11
branch: go-cli
id: l-18b2d758
scope: repo
status: active
replaces: l-e65fc8b4
src: agent
touches:
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/ContainerList.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/web/BindContainerPage.java
  - dashboard/src/main/resources/static/bind.js
  - cli/internal/command/register_container.go
---

## Decided
- BindContainerPage has one field, target, for a container name or project/service. A Docker container name cannot hold /, so the form splits at the first / and needs no kind radio. BindContainerPagesController maps the container, composeProject and composeService errors to the target field.
- The target list is a role=listbox under the input, filled by bind.js. The port field is a second listbox that bind.js fills.
- A target that a Service follows already is not refused. The list shows "used by <Services>" beside it, and you can pick it.
- A Compose target is followed only by a Binding to the same project and service (ContainerList.following), not by a name Binding of one of its containers.
- lvh-yard register container posts to container-bindings, reads the port from GET containers when --port is missing, then reads the Service for its hostname and warnings.

## Watch out
- Docker lists no ports for a stopped container, so FakeDocker.stop keeps the container in a stopped map with no ports.
