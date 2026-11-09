---
title: "The bind form has one target field, and a small script makes it a list you can type in"
author: Pablo
date: 2026-11-09
branch: worktree-agent-a14f9ef5631efb3e3
cost: { minutes: 19, tokens: 142908 }
id: l-b02726cc
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ContainerList.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/RegisterContainer.java
---

Decided: BindContainerPage has one field, target, for a container name or project/service. A Docker container name cannot hold /, so the form splits at the first / and needs no kind radio. BindContainerPagesController maps the container, composeProject and composeService errors, and FollowedContainerException, to the target field. The list is a role=listbox under the input, filled by bind.js. A followed target has aria-disabled, and bind.js refuses to pick it. A datalist was ruled out: browsers hide a disabled option, but the ticket wants a followed target shown. The port field is an input with a datalist; the server fills it with the ports of the typed target after a refused post, and bind.js refills it on each pick. Without the script, both are plain text fields. A Compose target is followed only by a Binding to the same project and service (ContainerList.following), not by a name Binding of one of its containers. ContainerBindingsCard keeps the count such as 3 containers, 2 passing, because BoundInstancesComponentTest wants addresses only in the Instances card. Watch out: Docker lists no ports for a stopped container, so FakeDocker.stop keeps the container in a stopped map with no ports. The CLI register container now posts to container-bindings, reads the port from GET containers when --port is missing, then reads the Service for its hostname and warnings.
