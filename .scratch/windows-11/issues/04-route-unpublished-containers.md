# 04: Route containers without published ports

**What to build:** A Windows developer creates Container Bindings for containers without published ports and reaches those Instances through Local Publications, even when the containers belong to unrelated Docker networks.

**Blocked by:** 03: Set up a private local Windows Workstation.

**Status:** ready-for-agent

- [ ] Bind containers by the existing supported container-name and Compose project/service selectors without requiring published ports or a shared Docker network.
- [ ] On real Docker Desktop, run representative containers on unrelated Docker networks. Each selected container contributes the expected Bound Instance at a working endpoint.
- [ ] Consul checks, dashboard Reachability and real requests through Traefik all succeed for those Local Publications. Merely observing registrations is insufficient.
- [ ] Stopping and restarting a selected container updates its Bound Instances and restores working routing without requiring manual address edits.
- [ ] Keep existing Scope and Out-of-range Instance rules. Do not imply that a private container address becomes LAN-reachable merely because local routing works.
- [ ] Reuse the running-Workstation and Reachability test patterns. Record real Windows results rather than treating simulated LAN forwarding or mocks as runtime proof.
- [ ] Update Container Binding guidance where Windows behavior needs explanation. A failed unpublished-container path blocks this ticket; requiring published ports is not an approved fallback.
