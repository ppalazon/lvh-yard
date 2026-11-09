---
title: "Tests take LAN ports from newLanPort, and YardReader reads Aliases from the Service record"
author: Pablo
date: 2026-11-09
branch: worktree-agent-a68043dbf4d606ee4
cost: { minutes: 32, tokens: 191920 }
id: l-39b8f580
scope: repo
status: active
replaces: l-71b50d61
src: agent
touches:
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/DashboardComponentTest.java
---

## Decided
- Tests that listen at the test Workstation's LAN address take their port from DashboardComponentTest.newLanPort, not freePorts(1). Two freePorts(1) calls in a row can return the same port (4 times in 20,000 pairs on the dev machine). The same host and port registers the same Instance again, so a test that waits for two Static Instances gets one and times out.
- listenOnLanAddress and answerHttpOnLanAddress wait until the port accepts a connection from the test. So a listener that never starts fails at once with its address, not as a Route timeout.
- The old part about AliasKeys no longer holds. AliasKeys is gone. YardReader reads the Aliases of a Service from its Service record (ServiceRecords), not from Aliases, because Aliases depends on YardReader.

## Tried and ruled out
- A busy port in the Consul container: busybox nc still listens on a port that an open outgoing connection uses as its source port (tried in hashicorp/consul:2.1.0).

## Watch out
- If a Route test times out, read which addresses the Route had. One Static address means a lost Instance. Every address means no check passed.

In the code: dashboard/src/test/.../DashboardComponentTest.java (newLanPort, listenOnLanAddress, answerHttpOnLanAddress), services/YardReader.java.
