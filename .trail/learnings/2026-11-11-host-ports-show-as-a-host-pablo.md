---
title: "Host ports show as a Host Port entry on the Service page and as address:port in lvh-yard containers"
author: Pablo
date: 2026-11-11
branch: go-cli
id: l-2657b2e8
scope: repo
status: active
replaces: l-f80e1af5
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/InstanceViews.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ListedContainer.java
  - cli/internal/command/containers.go
---

## Decided
The Containers page and its "host:<port> -> <container port>" text are gone.
The Instances of the Service page show the Host Port as a Host Port entry (InstanceViews.java). It shows the host port number, or "Not published" for a Container Port with no host port.
`lvh-yard containers` names the real host address, such as 0.0.0.0:37769->8080, and puts an IPv6 address in brackets.

## Tried and ruled out
Showing each port binding as it comes from Docker: ListedContainer drops repeated ports (distinct), so a port that Docker lists twice shows once.

## Watch out
Docker lists no ports for a stopped container.

In the code: dashboard/.../web/InstanceViews.java, services/ListedContainer.java (of), cli/internal/command/containers.go.
