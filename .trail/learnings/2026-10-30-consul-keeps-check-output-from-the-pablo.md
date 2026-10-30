---
title: "Consul keeps check output from the catalog and never gives back an HTTP check's URL, so LVH Yard works around both"
author: Pablo
date: 2026-10-30
branch: main
cost: { minutes: 17, tokens: 163512 }
id: l-dd6b03b1
scope: repo
status: active
src: agent
touches:
  - dashboard/src/test/java/dev/palazon/lvhyard/dashboard/DashboardTest.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/services/Registrations.java
  - dashboard/src/main/java/dev/palazon/lvhyard/dashboard/web/InstanceForm.java
  - compose.yaml
---

## Decided
- compose.yaml and the test Consul set check_update_interval = "0s". By default Consul copies a check's new output to the catalog only every 5 minutes while its status stays the same (critical stays critical), so the API showed an empty output for a failing HTTP check. Status changes were always synced; output was not. The Yard reader reads the catalog, as every Workstation will, so the fix belongs in Consul, not in reading the agent's local checks.
- Consul gives back a check's type, interval and timeout (as Go durations, such as 1m30s or 1.5s), but not its HTTP URL, in the catalog or the agent API. The HTTP path is kept in the reserved meta key lvh-yard-http-path, next to lvh-yard-instance. Meta keys starting with lvh-yard- or consul- are refused from users, and the API hides lvh-yard- keys.
- The API takes and gives durations as a whole number and one unit (10s, 1500ms). Intervals under 1s are refused: Consul would silently raise them to 1s.
- Editing an Instance (PUT .../instances/{id}) replaces it whole, as registering does: a part left out gets its default, not its old value. Moving it onto another Static Instance's host and port is refused.

## Tried and ruled out
- Pointing a test HTTP check at Consul's own API for a 404: Consul gave that check an empty output. Tests answer HTTP with a busybox nc loop in the Consul container instead (DashboardTest#answerHttpOnLanAddress).

## Watch out
- Tests share one Consul and its nc listeners. Give each test its own port or a host nothing listens on (10.1.2.3): a second TCP check on the same nc -lk listener made instanceWithoutCheckGetsTcpCheckOnItsPort flaky.
- The Instance list's checks include the Workstation's node check (Serf Health Status), so a Workstation problem shows on every Instance.

In the code: compose.yaml; dashboard/.../services/Registrations.java (checked, consulCheck, HTTP_PATH), YardReader.java (instance, goDuration); dashboard/src/test/.../TestcontainersConfiguration.java, DashboardTest.java.
