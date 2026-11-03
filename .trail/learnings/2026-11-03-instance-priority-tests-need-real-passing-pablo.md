---
title: "Instance Priority tests need real passing checks, and the remote Workstation restarted with narrower out-of-range"
author: Pablo
date: 2026-11-03
branch: worktree-agent-a70efe020d1b28d5c
cost: { minutes: 1, tokens: 30536 }
id: l-437bba10
scope: repo
status: active
src: agent
touches:
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/DashboardTest.java
---

## Decided
- The Route choice (Routing.of) lives in the Service record, built per Workstation in YardReader, so RouteBuilder, the API, the page and lvh-yard list all read the same choice. RouteBuilder keeps only the Instances in service.routing().
- Publication carries priority (default static, bound, alias) but hides it from JSON; the Service shows it as its own priority field, as the spec asks.
- Bound Instances for tests run on the test Yard's Docker network (startYardNetworkContainer). Containers on Docker's default bridge cannot be reached by the test Consul, so their checks never pass.
- stopListeningOnLanAddress kills the nc loop first with pkill -f, then nc, so the loop starts no new nc.

## Tried and ruled out
- Testing the cross-Workstation choice with Instances registered on ws-remote: every reachable test address is a container address (172.16/12), so it is out of range on ws-test and gets no Route. Instead ws-test is the Home Workstation and ws-remote is restarted with --lvh-yard.out-of-range.ranges=127.0.0.0/8,::1/128 (remote.whileDashboardRunsWith).
- One InstancePriorityTest class: about 55 s, as each Bound Instance waits up to 10 s for its first check (its interval cannot be set). Split into three classes so surefire runs them side by side; mise run check stays about 36 s.

## Watch out
- A Route to every Instance is also what you get before any check has run. A test that waits for a Route must first wait until the Instances it compares exist and pass (boundInstance waits for a passing Bound Instance), or it passes too early.
- Through Traefik, round robin can still reach the old Instance for one 1 s poll after the dashboard switches. The end-to-end test waits for six requests in a row that never reach the other copy.
