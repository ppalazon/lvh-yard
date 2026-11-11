---
title: Reading and writing Consul from the dashboard
author: Pablo
date: 2026-11-11
branch: go-cli
id: l-897a4503
scope: repo
status: active
replaces: l-17306754
src: agent
touches:
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/consul/ConsulAgent.java
  - dashboard/src/main/java/cc/lvhy/yard/dashboard/services/Registrations.java
  - dashboard/src/test/java/cc/lvhy/yard/dashboard/TestcontainersConfiguration.java
  - dashboard/src/test/java/cc/lvhy/yard/dashboard/DashboardIT.java
---

## Decided
- Consul is called through RestClient.builder() in ConsulAgent.java, not an injected RestClient.Builder. Spring Boot 4 only offers that bean with the spring-boot-starter-restclient module, which this build does not have.
- A Static Instance's Consul ID is a random UUID, reused when the same Service is registered again at the same host and port. An ID built as service-host-port clashed (web at dev-box and web-dev at box both gave web-dev-box-80, and Consul silently replaced one with the other), and it would change when a host or port is edited.
- Hosts are limited to letters, digits, ., - and :, so an Instance's address never breaks the dashboard's URLs.

## Watch out
- Jackson 3 fails on a missing JSON number mapped to a primitive (FAIL_ON_NULL_FOR_PRIMITIVES is on). Consul leaves out Port when it is 0 and Meta when empty, so AgentService uses Integer and defaults both; with int, one such registration broke the whole Catalog.
- Tests that need something listening on the Workstation use listenOnLanAddress. DashboardIT runs busybox nc inside the Consul container, so the TCP check reaches it at the container's address. DashboardComponentTest has a fake of the same method.
