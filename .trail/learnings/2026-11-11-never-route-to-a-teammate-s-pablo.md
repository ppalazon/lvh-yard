---
title: "Never route to a teammate's loopback or Docker container address"
author: Pablo
date: 2026-11-11
branch: go-cli
id: l-c7467a7c
scope: repo
status: active
replaces: l-da51cbb5
src: agent
touches:
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/OutOfRange.java
  - "dashboard/src/main/java/cc/lvhy/yard/dashboard/services/Routing.java#inRange"
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/YardReader.java
  - dashboard/src/main/resources/application.yaml
  - dev/workstations.sh
---

## Watch out
- A Route to a remote Instance on loopback, or on Docker's default bridge addresses, would reach a container or port on this computer instead of the teammate's. Such Instances are marked out of range and get no Route.
- In the code: YardReader sets Instance.outOfRange from the OutOfRange ranges, and Routing.inRange leaves those Instances out of every Route. Default ranges are loopback plus 172.17.0.0/16 to 172.31.0.0/16 (application.yaml). The YARD_OUT_OF_RANGE setting replaces the whole list, and a bad range stops startup. The test Yard leaves loopback out (dev/workstations.sh) because both test Workstations share this computer's loopback.
