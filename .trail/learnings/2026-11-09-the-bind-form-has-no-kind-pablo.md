---
title: The bind form has no kind field any more
author: Pablo
date: 2026-11-09
branch: worktree-agent-a68043dbf4d606ee4
cost: { minutes: 30, tokens: 191920 }
id: l-4bfec2fb
scope: repo
status: active
replaces: l-635f8684
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/BindContainerPage.java
---

## Decided
Nothing in the old learning holds now. ContainersPage and its kind radio buttons are gone. The bind form has one target field, as the learning "The bind form has one target field" says.
An empty Service name is no longer a case: the bind form opens from a Service page, so the Service is always known.

## Watch out
ListedContainer.proposedService still cleans a Compose service name or a container name into a name that the DNS label rule accepts.

In the code: dashboard/.../web/BindContainerPage.java, BindContainerPagesController.java, services/ListedContainer.java (proposedService).
