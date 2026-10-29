---
title: Reading and writing Consul from the dashboard
author: Pablo
date: 2026-10-29
branch: main
cost: { minutes: 1, tokens: 1476 }
id: l-17306754
scope: repo
status: active
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/consul/Consul.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Registrations.java
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/TestcontainersConfiguration.java
---

## Decided
- Consul is called through RestClient.builder() in Consul.java, not an injected RestClient.Builder. Spring Boot 4 only offers that bean with the spring-boot-starter-restclient module, which this build does not have.
- A Static Instance's Consul ID is a random UUID, reused when the same Service is registered again at the same host and port. An ID built as service-host-port clashed (web at dev-box and web-dev at box both gave web-dev-box-80, and Consul silently replaced one with the other), and it would change when ticket 04 edits a host or port.
- Hosts are limited to letters, digits, ., - and :, so an Instance's address never breaks the dashboard's URLs.

## Watch out
- Jackson 3 fails on a missing JSON number mapped to a primitive (FAIL_ON_NULL_FOR_PRIMITIVES is on). Consul leaves out Port when it is 0 and Meta when empty, so AgentService uses Integer and defaults both; with int, one such registration broke the whole Catalog.
- The test Consul's container bean is named consulContainer: a bean named consul clashes with the Consul component.
- Tests that need something listening on the Workstation run busybox nc inside the Consul container (DashboardTest#listenOnLanAddress), so the TCP check reaches it at the container's address.
