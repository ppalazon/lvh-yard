# Select Bound Instance addresses by Publication Scope

Status: ready-for-agent

Type: Story

## Problem Statement

When I publish a Service with a Container Binding, I want its Bound Instances to use addresses suited to the Publication's Scope. This should work the same way for Compose services and standalone containers.

Today, a wildcard Host Port mapping selects the Workstation's LAN address even for Local Scope. Host-network containers also select the LAN address. Changing a Publication does not itself reconcile Container Bindings, so address selection cannot follow Scope changes without a new synchronization trigger.

I also need Docker's actual bindings to remain authoritative. Selecting the LAN address cannot make a loopback-only Host Port available there. Selecting loopback cannot make a LAN-only binding accept traffic there.

## Solution

For wildcard Host Port mappings, use loopback with Local Scope and the Workstation's LAN address with Yard Scope. Preserve explicit Docker bind addresses and their Host Ports. When there are several mappings, prefer one suited to the Scope.

If the Container Port has no Host Port, retain the container address and Container Port. Host-network containers are the exception: use the Workstation address suited to the Scope and the Container Port.

Reconcile Bound Instances when a Publication is added, its Scope changes, or it is removed. Keep the current selection rule while a Service is Unpublished. Preserve the existing warnings and remote-route exclusions for Out-of-range Instances.

Local Scope continues to control who gets a Route. It does not promise private access or change Docker's configuration.

## User Stories

1. As a Workstation user, I want a Local Publication to use loopback for a wildcard Host Port mapping, so that its Route uses a local target.
2. As a Workstation user, I want a Yard Publication to use the Workstation's LAN address for a wildcard Host Port mapping, so that other Workstations receive a suitable target.
3. As a Workstation user, I want a standalone container to follow these rules, so that I do not have to maintain its Instance address manually.
4. As a Workstation user, I want a Compose service to follow the same rules, so that my choice of Docker workflow does not change Publication behavior.
5. As a Workstation user, I want every matching Compose container to select its own target, so that a scaled Service uses each container's actual mappings.
6. As a Workstation user, I want an explicit loopback binding preserved, so that LVH Yard does not invent a LAN target where Docker does not listen.
7. As a Workstation user, I want an explicit LAN binding preserved under Local Scope, so that LVH Yard does not invent a loopback target where Docker does not listen.
8. As a Workstation user, I want each selected mapping to retain its Host Port, so that my Route reaches the correct Container Port.
9. As a Workstation user, I want Local Scope to prefer a loopback-suitable mapping when several mappings exist, so that it avoids an unnecessary LAN target.
10. As a Workstation user, I want Yard Scope to prefer a LAN-suitable mapping when several mappings exist, so that it avoids a loopback-only target when another mapping is available.
11. As a Workstation user, I want selection ties to retain the existing rule, so that this feature does not introduce unrelated mapping preferences.
12. As a Workstation user, I want an existing mapping retained when none suits the Scope, so that LVH Yard reports the limitation instead of fabricating a target.
13. As a Workstation user, I want a container without a Host Port to keep its container address, so that my local Route can still reach it.
14. As a Workstation user, I want that fallback to use the Container Port, so that it does not confuse a container target with a host mapping.
15. As a Workstation user, I want Yard publication to remain allowed for a container-address fallback, so that useful local routing remains available.
16. As a Workstation user, I want the existing Yard warning for a Container Port without a Host Port to remain visible, so that I can see the setup limitation.
17. As a user on another Workstation, I want Out-of-range Instances excluded from my Routes, so that those Routes do not use the other Workstation's loopback or Docker bridge address.
18. As a Workstation user, I want a Local host-network container to use loopback, so that it follows the same Local address preference.
19. As a Workstation user, I want a Yard host-network container to use the LAN address, so that its target suits other Workstations.
20. As a Workstation user, I want a host-network target to retain the Container Port, so that no nonexistent Docker mapping is required.
21. As a Workstation user, I want adding a Publication to update existing Bound Instances, so that I do not have to restart their containers.
22. As a Workstation user, I want changing Local Scope to Yard Scope to update the targets, so that publication changes take effect without rebinding.
23. As a Workstation user, I want changing Yard Scope to Local Scope to update the targets, so that the local address preference returns without rebinding.
24. As a Workstation user, I want removing a Publication to restore the Unpublished selection rule, so that a missing Scope has no implicit Local meaning.
25. As a Workstation user, I want a Service created without a Publication to retain its current selection behavior, so that this feature does not change my Unpublished registrations.
26. As a Workstation user, I want Services sharing a container to select targets independently, so that a Local Publication does not change another Service's Yard target.
27. As a Workstation user, I want containers discovered after publication to use the saved Scope, so that startup order does not determine their addresses.
28. As a Workstation user, I want target changes to preserve my Container Bindings and Instance identities, so that a Scope change does not look like a different container.
29. As a Workstation user, I want the Service details and its Route to use the reconciled target, so that the displayed address agrees with where traffic is sent.
30. As a Workstation user, I want the documentation to distinguish Local routing from private access, so that I know when Docker or firewall configuration is still needed.
31. As a Workstation user, I want LVH Yard to leave Docker bindings and process listen addresses alone, so that publishing a Service does not reconfigure its process.
32. As a Workstation user, I want startup and container-event reconciliation to keep using the saved Publication, so that the selected targets remain correct after restarts or container replacement.

## Implementation Decisions

- Reuse the existing Container Binding reconciliation module. Apply one selection rule to both standalone and Compose bindings, once per matching running container and the Container Port named by the Binding.
- Read the saved Publications for the Home Workstation during reconciliation. A Service with no Publication follows the existing Unpublished selection rule.
- For published Services, apply the following target rules. A wildcard means an address that the existing Docker adapter and selector already treat as every host address.

  | Container networking                   | Local target                         | Yard target                                |
  | -------------------------------------- | ------------------------------------ | ------------------------------------------ |
  | Host Port on a wildcard address        | `127.0.0.1` and Host Port            | Workstation LAN address and Host Port      |
  | Host Port on an explicit address       | Explicit address and Host Port       | Explicit address and Host Port             |
  | No Host Port, with a container address | Container address and Container Port | Container address and Container Port       |
  | Host-network container                 | `127.0.0.1` and Container Port       | Workstation LAN address and Container Port |

- Select among published mappings before applying the table. For Local Scope, prefer loopback or wildcard mappings. For Yard Scope, prefer wildcard mappings or explicit addresses that the existing Out-of-range rules permit for remote routing.
- Within the preferred set, retain the existing mapping selector. If the preferred set is empty, run that selector over all published mappings. Preserve the address and Host Port of the selected explicit mapping.
- Do not substitute a container address merely because a published mapping is unsuitable for the Scope. The container-address fallback applies when the selected Container Port has no Host Port.
- Keep the existing treatment of wildcard address values. This feature does not redesign address-family selection or infer new Docker networking modes from missing addresses.
- Extend the Publication mutation flow to trigger Container Binding reconciliation after the saved Publication is added, its Scope changes, or it is removed. Preserve an acyclic dependency between Publication storage and binding reconciliation.
- Keep startup and Docker-event reconciliation. Each reconciliation uses the saved Publication rather than a Scope cached when the Binding was created. Publication changes must work without a Docker event or container restart.
- Keep the existing handling of unavailable Docker or Consul dependencies. Do not add a retry service or polling loop. When dependencies recover, the existing reconciliation mechanism must apply the latest saved Publication.
- Keep the existing Bound Instance registration identities and metadata. An address or selected Host Port can change without replacing the Container Binding or introducing a second logical Instance for the same match.
- Preserve warning behavior for Yard Publications with loopback targets or Container Ports without Host Ports. Preserve the existing Out-of-range classification and remote-route exclusion, including configured range overrides.
- Keep the existing Publication, Container Binding and Instance payload shapes. No new CLI flags, Scope values, API fields, Backup fields or stored binding fields are required.
- Keep Static Instances, Foreign Instances, Aliases and Instance Priority behavior unchanged. Scope-dependent target selection belongs to Bound Instances.
- Update the user guide when implementing the feature. Adjust any glossary sentence that describes the old target-selection rule, while keeping the glossary focused on domain terms. ADR-0012's host-networking decision remains unchanged.

## Testing Decisions

- The user confirmed the existing dashboard HTTP boundary as the testing seam. Use the existing component-test harness with fake Docker and Consul for the behavior matrix. No new production test seam is needed.
- A good test changes observable container or Publication state through the existing interfaces, then checks Service details, warnings or Route targets. It must catch a wrong address, wrong port, stale target, or incorrect remote-route inclusion.
- Test the Container Binding and Publication modules together through HTTP. Read the HTTP-provider configuration to verify the resulting Route targets. Do not test private selectors, dependency calls, source text or forwarding behavior.
- Follow the existing `ContainerBindingsComponentTest` patterns for binding a running container, awaiting its registered target, inspecting Container Port and Host Port metadata, and handling Compose matches.
- Follow the existing `PublicationsComponentTest` patterns for publishing, changing Scope and unpublishing. Extend those scenarios to exercise real Bound Instance address transitions without producing a Docker event.
- Follow the existing `TraefikConfigurationComponentTest` patterns for checking selected targets and excluding remote loopback and Docker bridge targets. Keep existing coverage when it already proves an unchanged rule.
- Cover each row of the selection table under both Scopes. Assert the complete address and port pair, not only Instance counts or successful HTTP status codes.
- Cover separate loopback and LAN mappings with different Host Ports. Verify Scope-sensitive selection, tie behavior and fallback when no mapping suits the Scope.
- Cover the transition from Unpublished to Local, Local to Yard, Yard to Local, and published to Unpublished. Use a live binding throughout and assert that the target changes without restarting its container.
- Cover two Services following the same container under different Scopes. Assert independent targets so that Publication state cannot leak between Services.
- Cover a Compose Binding with multiple matching containers whose mappings differ. Assert each container's resulting target and retain exclusion of containers from another project or Compose service.
- Cover a container discovered after its Publication exists. Reuse existing restart and event coverage to avoid duplicate cases that prove the same path.
- Verify that an address transition retains the Instance identity and Binding association and does not leave the old target in the Route.
- Retain existing dependency-failure coverage. Add a regression case only if no existing test proves that reconciliation after recovery reads the latest saved Scope.
- Use bounded awaits already established by the component harness. Keep tests isolated and safe for the full suite; do not use sleeps or real network dependencies for the matrix.
- Use the existing `ReachabilityIT` harness for one focused smoke scenario with real Docker, Consul and Traefik. Publish a container with a wildcard Host Port, change Scope, and verify the selected target and successful routing after each change.
- The integration setup must give the health checker and Traefik the Workstation reachability required by ADR-0012. A fake passing check cannot prove that a real Docker port accepts traffic.
- Do not add a separate CLI or page test for unchanged payload forwarding. No additional selection-only unit-test seam is needed; the current `ContainerBindingsTest` covers input validation rather than routing behavior.
- For implementation verification, run the focused affected component tests, the focused integration scenario, and `mise run test:unit`. Run `mise run check` before committing. These are future checks, not results from this specification task.

## Out of Scope

- Enforcing private access through Local Scope, authentication, firewall rules or Docker binding changes.
- Starting, stopping, recreating or otherwise reconfiguring containers or their processes.
- Making Docker bridge addresses routable across the LAN or forwarding remote traffic through the Home Workstation.
- Refusing Yard publication solely because an Instance is Out-of-range or its Container Port has no Host Port.
- Changing Static Instance, Foreign Instance, Alias, Instance Priority or Route Profile semantics.
- New Scope values, networking modes, address-family policy, retry infrastructure, telemetry or discovery mechanisms.
- New API, CLI or Backup fields.
- Implementing the feature as part of this specification task.

## Further Notes

The user approved the product rules and then confirmed the HTTP testing seam. This ticket is ready for implementation without another design interview. The implementing agent owns implementation and verification; the maintainer reviews the result.

The [agreed design](../spec.md) is the frozen approval record from the discussion. This ticket owns the implementation scope and testing decisions. Its status does not mean that the feature is implemented or that runtime checks have passed.

Current-state evidence inspected for this ticket:

- [Domain vocabulary](../../../GLOSSARY.md): Service, Publication, Scope, Container Binding, Bound Instance, Container Port, Host Port and Out-of-range Instance.
- [Container Binding reconciliation](../../../dashboard/src/main/java/cc/lvhy/yard/dashboard/services/ContainerBindings.java): `sync()` and `boundInstance()` select and maintain registered targets.
- [Publication mutations](../../../dashboard/src/main/java/cc/lvhy/yard/dashboard/services/Publications.java): saved Publication changes currently write Consul state without reconciling Container Bindings.
- [Container watcher](../../../dashboard/src/main/java/cc/lvhy/yard/dashboard/services/ContainerWatcher.java): existing startup, Docker-event and recovery synchronization.
- [Binding component tests](../../../dashboard/src/test/java/cc/lvhy/yard/dashboard/ContainerBindingsComponentTest.java), [Publication component tests](../../../dashboard/src/test/java/cc/lvhy/yard/dashboard/PublicationsComponentTest.java), [Route configuration tests](../../../dashboard/src/test/java/cc/lvhy/yard/dashboard/TraefikConfigurationComponentTest.java) and [Reachability integration tests](../../../dashboard/src/test/java/cc/lvhy/yard/dashboard/ReachabilityIT.java): prior art for the confirmed seam.
- [ADR-0008](../../../docs/adr/0008-unit-component-and-integration-tests.md): HTTP component tests with contract-backed fakes, plus focused real-system integration tests.
- [ADR-0012](../../../docs/adr/0012-services-keep-host-networking.md): host networking for Consul, Traefik and the dashboard.

Selecting an address cannot make the process listen there. A host-network process listening only on loopback still cannot accept connections at the Workstation's LAN address. Existing Health and Reachability checks remain responsible for reporting whether the chosen target works.
