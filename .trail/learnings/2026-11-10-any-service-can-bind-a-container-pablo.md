---
title: Any Service can bind a container that another Service already follows
author: Pablo
date: 2026-11-10
branch: main
id: l-751f5ba7
scope: repo
status: active
replaces: l-f7f48689
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ContainerBindings.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ContainerList.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/backups/ImportPlanner.java
---

## Decided
- The user dropped the rule that a container or Compose service has one Container Binding only. ContainerBindings.add refuses nothing for a followed target: any number of Services can bind it, on any port, and the same Service can bind one container on two ports. It still throws for a missing Service. FollowedContainerException is gone, with its 409.
- The user chose the simplest rule even where it makes little sense: the same Service may bind one container on two ports. Binding the same target and port to the same Service again writes the same key, so it is a no-op that still answers 201.
- ListedContainer.followedBy is a list of Service names, always present and empty when nothing follows. The user said the API is not released, so it changed with no compatibility field.
- The bind form shows 'used by <Services>' on such a target and lets you pick it. ImportPlanner no longer plans a problem for a target that the file or the Workstation already binds, and its one-Binding messages are gone.

## Watch out
- A Compose target is still followed only by a Binding to the same project and service, not by a name Binding of one of its containers. ContainerBindings.sameTarget keeps the two apart, and a container bound both ways gives two Bound Instances.
- The CLI tests check every fixture against dashboard/openapi.yaml, so a container fixture needs followedBy: [] since the field is required.
