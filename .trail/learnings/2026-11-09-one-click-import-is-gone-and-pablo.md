---
title: "One-click Import is gone, and a followed container is still refused"
author: Pablo
date: 2026-11-09
branch: worktree-agent-a68043dbf4d606ee4
cost: { minutes: 32, tokens: 191920 }
id: l-e1132500
scope: repo
status: active
replaces: l-228f65e3
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ContainerBindings.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ListedContainer.java
---

## Decided
The Containers page, its one-click Import, ContainerImport and UnfollowedContainer are gone. A Container Binding is made only from the bind form of a Service page, or from `lvh-yard register container`.
These parts of the old learning still hold:
- A container that a Container Binding of this Workstation already follows is refused with FollowedContainerException (409, names the Service). A second Binding of it only adds a second Route target.
- The proposed name falls back from the cleaned Compose service to the cleaned container name to "container". Registrations.isDnsLabel must accept each one. So the cleaning cannot drift from the name rule.

## Watch out
A Compose target is followed only by a Binding to the same project and service, not by a name Binding of one of its containers.

In the code: dashboard/.../services/ContainerBindings.java (FollowedContainerException), ListedContainer.java (of, proposedService), ContainerList.following.
