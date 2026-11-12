# Select Bound Instance addresses by Publication Scope

**Agreed design for Compose services and standalone containers. Not implemented.**

| Status             | Owner               | Audience             | Scope                                    |
| ------------------ | ------------------- | -------------------- | ---------------------------------------- |
| Agreed design only | LVH Yard maintainer | LVH Yard maintainers | Address selection for Container Bindings |

## 1. Summary

Use the Publication's Scope to choose the target address when Docker provides a wildcard Host Port mapping. Preserve explicit Docker bind addresses. Keep the container address as the fallback when there is no Host Port, except for host-network containers.

This file owns the planned selection rules. The [glossary](../../GLOSSARY.md) owns domain terms, and the [user guide](../../docs/user-guide.md) describes current behavior.

## 2. Grounding

The following sources were inspected in the working tree on 2026-11-12. The repository's HEAD was `33d7e5953b8a77d84bd78e75fad44c701e82c2ca`; this is not a claim that the working tree matches that revision.

| Source                                                                                                                                         | Observed contract or behavior                                                                                                                                                                        |
| ---------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [GLOSSARY.md](../../GLOSSARY.md), Publication and Scope                                                                                        | Scope controls who gets a Route. It does not control who can connect.                                                                                                                                |
| [ContainerBindings.java](../../dashboard/src/main/java/cc/lvhy/yard/dashboard/services/ContainerBindings.java), `sync()` and `boundInstance()` | Selection does not use Scope. Wildcard mappings use the LAN address; explicit mappings keep their address. Without a mapping, selection uses the container address or falls back to the LAN address. |
| [Docker.java](../../dashboard/src/main/java/cc/lvhy/yard/dashboard/docker/Docker.java), `Port` and `Network`                                   | A port mapping carries its bind address, Container Port and Host Port. A host-network container has no separate container address.                                                                   |
| [ADR-0012](../../docs/adr/0012-services-keep-host-networking.md)                                                                               | Consul, Traefik and the dashboard use host networking to reach Instances on the Workstation.                                                                                                         |
| [GLOSSARY.md](../../GLOSSARY.md), Out-of-range Instance                                                                                        | Other Workstations exclude Out-of-range Instances from their Routes.                                                                                                                                 |

No runtime checks were run for this design discussion.

## 3. Goals and non-goals

The goal is to select a target suited to the Publication's Scope while keeping Docker's actual address and port pairings intact. Compose services and standalone containers must use the same rules.

The design does not change Docker port mappings, process listen addresses, firewall rules, Static Instances or Foreign Instances. Local Scope keeps its existing meaning. This task records the design without changing runtime code, the glossary or current user documentation.

## 4. Selection rules

The following table defines the planned target for each matching container and the Container Port named by its Container Binding.

| Container networking                   | Local Publication                    | Yard Publication                           |
| -------------------------------------- | ------------------------------------ | ------------------------------------------ |
| Host Port on a wildcard address        | `127.0.0.1` and Host Port            | Workstation LAN address and Host Port      |
| Host Port on an explicit address       | Explicit address and Host Port       | Explicit address and Host Port             |
| No Host Port, with a container address | Container address and Container Port | Container address and Container Port       |
| Host-network container                 | `127.0.0.1` and Container Port       | Workstation LAN address and Container Port |

> **Why & What - Choose scope-suitable targets**
>
> **What:** Apply the table to published Services. For multiple mappings, prefer loopback or wildcard mappings for Local Scope and LAN-suitable or wildcard mappings for Yard Scope.
>
> **Why:** The selected target should suit the Workstations that receive Routes. Keeping explicit bind addresses avoids selecting an address where Docker does not publish the port.
>
> **Alternatives considered:** Scope-independent selection keeps registrations stable across Publication changes, but misses the requested Local loopback preference. Selecting loopback or LAN unconditionally is simpler, but can contradict an explicit Docker binding.
>
> **Fallback:** Keep the existing mapping selector for ties and when no mapping suits the Scope. Preserve the selected mapping's address and Host Port.

> **Why & What - Retain container address fallback**
>
> **What:** Allow the container-address fallback for Yard Publications. Keep the existing Out-of-range warnings and exclusion from remote Routes.
>
> **Why:** A Home Workstation can retain its working Route even when other Workstations cannot reach the target. Yard Scope cannot make a Docker bridge address LAN-routable.
>
> **Alternatives considered:** Refusing such Yard Publications would prevent an incomplete Yard Route, but would also prevent publication while some targets remain useful locally.
>
> **Fallback:** When no separate container address exists for a host-network container, use the Workstation address specified in the table.

> **Why & What - Keep unpublished selection stable**
>
> **What:** Keep the current selection rule while the Service is Unpublished. Recompute Bound Instance targets when a Publication is added, its Scope changes, or it is removed.
>
> **Why:** An Unpublished Service has no Publication Scope. Preserving its current rule avoids introducing a second implicit Local Scope.
>
> **Alternatives considered:** Preferring loopback while Unpublished would simplify the default choice, but would change registrations before the user chooses to publish.
>
> **Fallback:** Removing a Publication restores the current Unpublished selection rule.

## 5. Acceptance criteria

Future implementation must demonstrate the following behavior:

- For a wildcard mapping, Local and Yard Publications select the respective targets in ch. 4.
- For an explicit loopback mapping, Yard Scope preserves loopback and retains the existing Out-of-range warning and remote-route exclusion.
- For an explicit LAN mapping, Local Scope preserves that LAN address rather than substituting loopback.
- For separate loopback and LAN mappings of one Container Port, Local selects the loopback mapping and Yard selects the LAN mapping, including their respective Host Ports.
- When several mappings suit a Scope, the existing selector breaks ties. When none suits it, that selector chooses the fallback.
- Without a Host Port, a container with its own address retains that address and Container Port under either Scope.
- For a host-network container, the target follows the corresponding row in ch. 4.
- Adding, changing or removing a Publication updates its Bound Instance targets without requiring a container restart.
- An Unpublished Service retains the current address-selection rule.
- Compose bindings apply these rules to every matching running container. Standalone bindings apply the same rules to their matching container.

## 6. Benefits, costs and limits

Local Publications can use loopback where Docker's binding permits it. Yard Publications can use the LAN address without changing Docker configuration.

A Publication change can change registered Instance addresses and requires synchronization with Container Bindings. Explicit bindings can still leave a Yard target Out-of-range. Container-address fallbacks can provide a local Route without providing a remote Route.

Selecting an address does not make a process listen there. In particular, a host-network process that listens only on loopback will not become reachable at the LAN address. Local Scope does not close a Host Port that Docker already publishes on the LAN.

No product decisions remain open from this discussion. Implementation and runtime verification are outside this task.

## 7. Approval

- 2026-11-12 user: approved these selection rules and recording the design only, without implementation or changes to documentation of current behavior.
