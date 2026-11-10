---
title: "The bind form has one target field, and a small script makes it a list you can type in"
author: Pablo
date: 2026-11-10
branch: go-cli-ticket-17
id: l-e65fc8b4
scope: repo
status: active
replaces: l-b02726cc
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ContainerList.java
  - cli/internal/command/register_container.go
---

Decided: BindContainerPage has one field, target, for a container name or project/service. A Docker container name cannot hold /, so the form splits at the first / and needs no kind radio. BindContainerPagesController maps the container, composeProject and composeService errors, and FollowedContainerException, to the target field. The list is a role=listbox under the input, filled by bind.js. A followed target has aria-disabled, and bind.js refuses to pick it. A datalist was ruled out: browsers hide a disabled option, but the ticket wants a followed target shown. The port field is an input with a datalist; the server fills it with the ports of the typed target after a refused post, and bind.js refills it on each pick. Without the script, both are plain text fields. A Compose target is followed only by a Binding to the same project and service (ContainerList.following), not by a name Binding of one of its containers. ContainerBindingsCard keeps the count such as 3 containers, 2 passing, because BoundInstancesComponentTest wants addresses only in the Instances card. Watch out: Docker lists no ports for a stopped container, so FakeDocker.stop keeps the container in a stopped map with no ports. lvh-yard register container posts to container-bindings, reads the port from GET containers when --port is missing, then reads the Service for its hostname and warnings.
