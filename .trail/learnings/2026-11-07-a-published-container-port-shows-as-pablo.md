---
title: "A published container port shows as host:<host port> -> <container port>, once for IPv4 and IPv6"
author: Pablo
date: 2026-11-07
branch: main
cost: { minutes: 15, tokens: 21203 }
id: l-c7d9c4bf
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/Problems.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/ErrorPage.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/DashboardTest.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/ErrorPagesTest.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/ProblemsTest.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/web/ErrorPageTest.java
  - docs/user-guide.md
---

## Decided
- The user asked for the docker ps style "0.0.0.0:37769 -> 8080" in place of "8080 on host port 37769", and then for "host:37769 -> 8080" so that the IPv4 and IPv6 bindings of one port show as one line.
- A port on every address (0.0.0.0, ::, or empty) is written host:<port>. A port on one address names it, such as 127.0.0.1:37769 -> 8080, because other Workstations cannot reach it. An IPv6 address goes in brackets.

## Tried and ruled out
- One line per host address, as docker ps does: Docker publishes most ports on both 0.0.0.0 and ::, so every port showed twice.

## Watch out
- The list drops lines with the same text (distinct), which is what folds the IPv4 and IPv6 bindings into one line.
- In page HTML, -> is written -&gt;, so tests look for " -&gt; 80".

In the code:
- ContainersPage.published(port), used by ContainersPage.ports. Tests in ContainersTest check "host:" + port + " -&gt; 80".
