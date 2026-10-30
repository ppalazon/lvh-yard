---
title: "Never route to a teammate's loopback or Docker container address"
author: Pablo
date: 2026-10-30
standing: true
id: l-da51cbb5
scope: repo
status: active
src: init-sessions
---

## Watch out
- A Route to a remote Instance on loopback, or on Docker's default bridge addresses, would reach a container or port on this computer instead of the teammate's. Such Instances are marked out of range and get no Route.
- In the code: Instance.outOfRange (renamed from unreachable) is checked in RouteBuilder. Default ranges are loopback plus 172.17.0.0/16 to 172.31.0.0/16. The YARD_OUT_OF_RANGE setting replaces the whole list, and a bad range stops startup. The test Yard leaves loopback out because both test Workstations share this computer's loopback.

_From past Claude sessions here, Oct 30._
