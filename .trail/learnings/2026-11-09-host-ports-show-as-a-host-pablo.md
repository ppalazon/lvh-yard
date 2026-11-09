---
title: "Host ports show as a Host Port column on the Service page and as address:port in lvh-yard containers"
author: Pablo
date: 2026-11-09
branch: worktree-agent-a68043dbf4d606ee4
cost: { minutes: 32, tokens: 191920 }
id: l-a2f58f75
scope: repo
status: active
replaces: l-c7d9c4bf
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/InstanceSourcesCard.java
  - cli/src/main/java/dev/palazon/lvhyard/cli/Containers.java
---

## Decided
The Containers page and its "host:<port> -> <container port>" text are gone.
The Instances table of the Service page has a Host Port column. It shows the host port number, or "Not published" for a Container Port with no host port.
`lvh-yard containers` names the real host address, such as 0.0.0.0:37769->8080, and puts an IPv6 address in brackets.

## Tried and ruled out
Showing each port binding as it comes from Docker: ListedContainer drops repeated ports (distinct), so a port that Docker lists twice shows once.

## Watch out
Docker lists no ports for a stopped container.

In the code: dashboard/.../web/InstanceSourcesCard.java (instanceTable), services/ListedContainer.java (of), cli/.../Containers.java (mappings).
