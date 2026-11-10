---
title: "Consul keeps check output from the catalog and never gives back an HTTP check's URL, so LVH Yard works around both"
author: Pablo
date: 2026-11-10
branch: main
cost: { minutes: 17, tokens: 163512 }
id: l-b253d62d
scope: repo
status: active
replaces: l-dd6b03b1
src: agent
touches:
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Registrations.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/InstanceForm.java
  - compose.yaml
---

## Decided
- compose.yaml and the test Consul set check_update_interval = "0s". By default, Consul copies a check's new output to the catalog only every 5 minutes while its status stays the same, so the API showed an empty output for a failing HTTP check. The Yard reader reads the catalog, so the fix belongs in Consul.
- Consul gives back a check's type, interval and timeout, but not its HTTP URL. The HTTP path is kept in the reserved meta key lvh-yard-http-path. Meta keys that start with lvh-yard- or consul- are refused from users, and the API hides lvh-yard- keys.
- Durations are a whole number and one unit (10s, 1500ms). Intervals under 1s are refused, because Consul would silently raise them to 1s.
- Editing an Instance replaces it whole, as registering does: a part left out gets its default, not its old value.

## Watch out
- Give each test its own port, or a host where nothing listens (10.1.2.3), because a second TCP check on the same listener made tests flaky.
- An Instance's checks include the node check (Serf Health Status), so a Workstation problem shows on every Instance.

In the code: compose.yaml, services/Registrations.java (HTTP_PATH), YardReader.java (goDuration), TestcontainersConfiguration.java.
