# 06: Route published container ports across scopes

**What to build:** A Windows developer binds containers with published ports and gets the correct endpoint for Local and Yard Publications. Routing honors wildcard and explicit address bindings without confusing Windows LAN identity with container binding.

**Blocked by:** 05: Join a mixed-platform Yard.

**Status:** ready-for-agent

- [ ] For a wildcard-published container port, a Local Publication uses a locally reachable endpoint and a Yard Publication uses the Home Workstation's reachable LAN address and Host Port.
- [ ] Test explicitly bound published ports separately, including loopback-only and LAN-reachable bindings. Preserve their actual reachability limits rather than silently widening exposure.
- [ ] Consul registration, Health checks, dashboard Reachability and Traefik agree on the endpoint. Prove actual responses locally and from another Workstation for the applicable Scope and binding.
- [ ] A loopback-only or otherwise Out-of-range Instance is not routed remotely merely because its Service has Yard Scope. Existing warnings and remote filtering remain accurate.
- [ ] Changing a Publication's Scope selects the appropriate endpoint without stale routing. Container restart or replacement updates the selected Host Port or address when it changes.
- [ ] Never use a VM-only address or host-relative Docker hostname as a substitute for the Yard-visible endpoint.
- [ ] Use the real mixed-platform Yard and existing Reachability test patterns. Document Windows port-binding limits and record the observed mapping and request results.
