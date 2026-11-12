# 03: Prefer Host Port mappings suited to Scope

**What to build:** When a Container Port has several Host Port mappings, select a mapping suited to the Publication's Scope before choosing its target address. Keep the selected mapping's address and Host Port together. Apply the choice independently to every matching container and Service, including scaled Compose services, and update it when Scope changes.

**Blocked by:** 02: Select Scope-aware Bound Instance targets. Its Scope-aware reconciliation and Publication lifecycle provide the behavior this ticket extends.

**Status:** ready-for-agent

Parent specification: 01: Select Bound Instance addresses by Publication Scope. This ticket completes mapping preference on top of ticket 02 without introducing another testing seam or target-selection path.

- [ ] For Local Scope, prefer loopback or wildcard mappings. For Yard Scope, prefer wildcard mappings or explicit addresses permitted by the existing Out-of-range rules for remote routing.
- [ ] Choose from the preferred mappings using the existing selector. Retain its wildcard-first preference and its existing order among equally ranked mappings; do not add a new lowest-port or address-order policy.
- [ ] When no mapping suits the Scope, use the existing selector over all published mappings. Preserve an explicit selected address and Host Port, and retain applicable warnings and remote-route exclusions rather than fabricating a reachable target.
- [ ] With separate loopback and LAN mappings of one Container Port on different Host Ports, Local selects the loopback mapping and Yard selects the LAN mapping. A Scope change updates both address and Host Port without a container restart or Docker event.
- [ ] When the chosen mapping is wildcard, apply ticket 02's Scope-dependent Workstation address. When it is explicit, preserve its actual address. Never combine the address from one mapping with the Host Port from another.
- [ ] An unsuitable published mapping does not trigger a container-address fallback. That fallback remains reserved for a Container Port with no Host Port.
- [ ] Each matching container in a scaled Compose service selects from its own mappings. Services following the same container under different Scopes select independently, without sharing a cached target.
- [ ] An Unpublished Service retains the original mapping-selection rule. Removing a Publication restores that rule, even if it selects a different mapping from the published Service.
- [ ] Extend existing dashboard HTTP component coverage for distinct loopback and LAN Host Ports, several preferred candidates, no preferred candidate, and Publication transitions. Check exact address and port pairs in Service details and resulting Routes.
- [ ] Cover a Compose Binding whose matching containers have different mapping sets, and two Services sharing one container under different Scopes. Assert concrete targets rather than counts or private helper results.
- [ ] Preserve ticket 02's Instance identities, metadata, single-mapping behavior, host-network behavior and no-Host-Port behavior. Reuse existing coverage rather than repeating the complete matrix or adding a separate unit-test seam.
- [ ] Update the user guide to explain Scope-sensitive mapping preference, existing tie-breaking and fallback when no mapping suits the Scope. Keep the distinction between Scope and private access explicit.
- [ ] Keep Docker configuration, payload shapes, address-family policy and Out-of-range classification unchanged. Do not introduce new retries, networking modes or proxy hops through the Home Workstation.
- [ ] Run the focused affected component tests and `mise run test:unit`. Before committing, run `mise run check`, which also exercises the real-routing coverage delivered by ticket 02.
