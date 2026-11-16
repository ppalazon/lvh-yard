# 06: Route published container ports across scopes

**What to build:** A Windows developer binds containers with published ports and gets the correct endpoint for Local and Yard Publications. Routing honors wildcard and explicit address bindings without confusing Windows LAN identity with container binding.

**Blocked by:** 05: Join a mixed-platform Yard.

**Status:** done

- [x] For a wildcard-published container port, a Local Publication uses a locally reachable endpoint and a Yard Publication uses the Home Workstation's reachable LAN address and Host Port.
- [x] Test explicitly bound published ports separately, including loopback-only and LAN-reachable bindings. Preserve their actual reachability limits rather than silently widening exposure.
- [x] Consul registration, Health checks, dashboard Reachability and Traefik agree on the endpoint. Prove actual responses locally and from another Workstation for the applicable Scope and binding.
- [x] A loopback-only or otherwise Out-of-range Instance is not routed remotely merely because its Service has Yard Scope. Existing warnings and remote filtering remain accurate.
- [x] Changing a Publication's Scope selects the appropriate endpoint without stale routing. Container restart or replacement updates the selected Host Port or address when it changes.
- [x] Never use a VM-only address or host-relative Docker hostname as a substitute for the Yard-visible endpoint.
- [x] Use the real mixed-platform Yard and existing Reachability test patterns. Document Windows port-binding limits and record the observed mapping and request results.

Accepted limit (maintainer, 2026-11-16): the Route flapping between 200 and 404 and the "Workstation unreachable" results during cross-datacenter reads on the
overloaded 2-CPU VM are not diagnosed (`evidence/06-route-published-container-ports.md`, "Load and stalls"). On the larger VM
(4 CPUs, 6 GB) with at most two stacks, the passes of `08` and `10` saw no stall. That does not explain the earlier ones.
