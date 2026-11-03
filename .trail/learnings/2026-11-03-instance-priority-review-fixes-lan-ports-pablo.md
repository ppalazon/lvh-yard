---
title: "Instance Priority review fixes: LAN ports and Alias keys"
author: Pablo
date: 2026-11-03
branch: worktree-agent-ad1ed6a950e93c2e1
cost: { minutes: 5, tokens: 66921 }
id: l-71b50d61
scope: repo
status: active
src: agent
touches:
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/DashboardTest.java
---

## Decided
- Tests that listen at the test Workstation's LAN address take their port from DashboardTest.newLanPort, not freePorts(1). Two freePorts(1) calls in a row can return the same port (4 times in 20,000 pairs on the dev machine). The same host and port registers the same Instance again. So InstancePriorityTest.everyPassingInstanceOfTheChosenKindSharesTheRoute then gets one Static Instance, not two. It waits 30 s for a second one that never comes.
- listenOnLanAddress and answerHttpOnLanAddress wait until the port accepts a connection from the test. So a listener that never starts fails at once with its address, not as a Route timeout.
- YardReader reads Alias targets through AliasKeys, the Consul KV store of Aliases. Aliases depends on YardReader, so YardReader cannot depend on Aliases.

## Tried and ruled out
- A busy port in the Consul container: busybox nc still listens on a port that an open outgoing connection uses as its source port (tried in hashicorp/consul:2.1.0).

## Watch out
- The 30 s timeout in everyPassingInstanceOfTheChosenKindSharesTheRoute failed once in three mise run check runs before this fix. The repeated port explains the symptom, but it is rare, so it is not proven to explain that rate. Four full runs after the fix passed. If it fails again, read which addresses the Route had: one Static address means a lost Instance, every address means no check passed.
