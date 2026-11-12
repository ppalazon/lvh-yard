# 02: Select Scope-aware Bound Instance targets

**What to build:** Make a Publication's Scope select the Bound Instance target for Compose services and standalone containers. For a wildcard Host Port mapping, Local uses loopback and Yard uses the Workstation's LAN address. Preserve explicit mappings and container-address fallbacks. Apply changes to running containers when the user publishes, changes Scope or unpublishes, without requiring a restart or a Docker event.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

Parent specification: 01: Select Bound Instance addresses by Publication Scope. That specification owns the full feature contract. Scope-sensitive preference among multiple Host Port mappings belongs to ticket 03.

- [ ] With one wildcard Host Port mapping, Local selects `127.0.0.1` and the Host Port; Yard selects the Workstation's LAN address and the same Host Port.
- [ ] An explicit Docker binding retains its exact address and Host Port under either Scope. Local does not substitute loopback for a LAN-only binding, and Yard does not substitute the LAN address for a loopback-only binding.
- [ ] When the selected Container Port has no Host Port and the container has its own address, either Scope selects that container address and Container Port. A Yard Publication remains allowed.
- [ ] A host-network container uses loopback under Local Scope and the Workstation's LAN address under Yard Scope, at the Container Port. Selection does not change where its process listens.
- [ ] An Unpublished Service retains the existing target-selection rule. Adding a Publication, changing Local to Yard, changing Yard to Local, and removing a Publication reconcile existing Bound Instances without restarting containers or producing Docker events.
- [ ] Standalone and Compose bindings follow the same rules. Each matching Compose container uses its own mapping, address and port; unrelated projects and Compose services remain excluded.
- [ ] Two Services following the same container can retain different targets under different Scopes. Their Publication state does not leak between Services.
- [ ] Containers discovered after publication, at dashboard startup or after a container event use the latest saved Publication. Existing dependency-recovery reconciliation also uses that saved state, without a new polling or retry mechanism.
- [ ] Target changes preserve Instance identities, Container Bindings and container metadata. Service details and Routes converge on the new target without leaving the old target routed or creating a duplicate logical Instance.
- [ ] Existing Yard warnings for loopback targets and Container Ports without Host Ports remain accurate. Other Workstations continue to exclude Out-of-range Instances, including configured range overrides.
- [ ] Reuse the confirmed dashboard HTTP testing seam with the existing fake Docker and Consul. Extend behavior coverage for the target rules and Publication transitions; assert complete address and port pairs, Instance identity, relevant warnings and resulting Route targets.
- [ ] Keep existing tests that already prove unchanged behavior. Add no private-selector, mock-call-count, source-text or payload-forwarding tests.
- [ ] Demonstrate one focused real Docker, Consul and Traefik routing scenario for a wildcard Host Port across Local and Yard Scope. Assert the selected target and successful routing, with health checking and proxy reachability consistent with ADR-0012.
- [ ] Update the user guide and any glossary sentence describing target selection. Explain that Local Scope controls Routes and does not enforce private access or change Docker bindings.
- [ ] Keep existing API, CLI and Backup shapes, Publication defaults, Static Instances, Foreign Instances, Aliases and Instance Priority unchanged. Preserve ADR-0012's host-networking decision and keep Publication storage and binding reconciliation dependencies acyclic.
- [ ] Run the focused affected component tests, the focused real-routing scenario and `mise run test:unit`. Before committing, run `mise run check`.
