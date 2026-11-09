---
title: "The Instance Priority is no longer in the Publication: the Service record holds the order and the toggle"
author: Pablo
date: 2026-11-09
branch: worktree-agent-a68043dbf4d606ee4
cost: { minutes: 32, tokens: 191920 }
id: l-85d02ac1
scope: repo
status: active
replaces: l-9b4fa25d
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/ServiceRecords.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Publications.java
---

## Decided
The Publication no longer holds an Instance Priority. Its KV value holds only the Scope, the Route Profiles and the access log. The order of Instance Sources and the Instance Priority toggle live in the Service record at lvh-yard/services/<svc>, as {"order": [source IDs], "priorityOff": true}. Defaults are left out.
The Service page still posts the whole new order from each Up or Down button, so it needs no script. It posts to /services/<ws>/<svc>/instance-priority, with the order as comma-separated source IDs and the toggle as on.
An unknown or repeated ID in an order is refused with InvalidPriorityException (400).

## Tried and ruled out
Kinds in the order (static, bound, alias): one Service can have many sources of one kind, and each one needs its own place in the order.

## Watch out
Unpublishing deletes only the Publication, so the order and the toggle stay.

In the code: dashboard/.../services/ServiceRecords.java (setPriority, ServiceRecord), Publications.value, PagesController.instancePriority.
