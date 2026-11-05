# One row per Service: the Degraded state, a Catalog without repeats, and container ports

Status: ready-for-agent

Words follow `GLOSSARY.md`. This spec adds the terms Degraded, Container Port and Host Port. It also changes Healthy: a Healthy Service now has only passing Instances.

## Problem Statement

The Catalog shows one row for each Instance. A Service with three Instances repeats its name, state and hostname three times. The Instance address takes a column, but you do not need it to find a Service.

A Service with one passing Instance and one critical Instance is Healthy today. You cannot see that one of its Instances fails.

The Container Bindings card on the Service page lists the address of every Bound Instance in the row of its Binding. A Compose service with several containers fills that row with addresses. The page does not show which container an Instance belongs to. It also does not show the Container Port and the Host Port, which are the ports that you think about.

`lvh-yard list` prints every Instance. No command gives the short view of the Catalog.

## Solution

A new Service state, Degraded, marks a published Service with at least one passing Instance and at least one Instance that does not pass. Healthy then means that every Instance passes. The state counts every Instance. It ignores the Instance Priority and the Out-of-range mark, so every Workstation shows the same state for a Service.

The Catalog shows one row for each Service: Service, State, Hostname and Instances. The Instances cell gives a count, such as "2 of 3 passing", or "No Instances".

The Container Bindings card keeps one row for each Binding. The list of addresses becomes a summary, such as "3 containers, 2 passing".

The Instances card gets the columns Container, Container Port and Host Port. The detail for each container lives there.

A new command, `lvh-yard catalog`, prints the Catalog view: one line for each Service.

## User Stories

1. As a developer, I want a Degraded state, so that I see a Service where some Instances fail.
2. As a developer, I want a Service with one warning Instance to be Degraded, so that the state matches what the Route uses.
3. As a developer, I want Healthy to mean that every Instance passes, so that Healthy means no problem.
4. As a developer, I want the state to count every Instance, so that it says what runs, whatever the Instance Priority.
5. As a developer, I want the same state on every Workstation, so that a teammate and I see the same thing.
6. As a developer, I want an Unpublished Service to stay Unpublished, so that my choice not to publish stays visible.
7. As a developer, I want a Degraded option in the Catalog State filter, so that I can list only Degraded Services.
8. As a developer, I want an orange Degraded badge, so that it differs from Healthy, Unhealthy and Unpublished.
9. As a developer, I want the warning Health badge in the same orange, so that grey means only Unpublished.
10. As a developer, I want one Catalog row for each Service, so that no Service repeats.
11. As a developer, I want an Instances count in the Catalog, so that I see the Health at a glance. An example is "2 of 3 passing".
12. As a developer, I want "No Instances" in the count of a Service without Instances, so that the cell is never empty.
13. As a developer, I want the count for an Unpublished Service too, so that I see its Health before I publish it.
14. As a developer, I want no Instance address in the Catalog, so that the list stays short.
15. As a developer, I want a summary in each Container Binding row, so that a Compose service takes one short row. An example is "3 containers, 2 passing".
16. As a developer, I want to keep the message for a Binding that matches no container, so that I know why it has no Instances.
17. As a developer, I want a Container column in the Instances card, so that I know which container gives each Bound Instance.
18. As a developer, I want a Container Port column, so that I see the port that the process listens on.
19. As a developer, I want a Host Port column, so that I see the port that is open on the Workstation.
20. As a developer, I want "Not published" in the Host Port cell of an Instance with no Host Port. Then I know that the Instance is at the container's own address.
21. As a developer, I want the Container Port as the Host Port of a container on the host network, so that its open port does not show as "Not published".
22. As a developer, I want empty container cells for a Static Instance, so that a Static Instance shows no false container.
23. As a developer, I want the container columns on a Remote Service too, so that I see a teammate's containers the same way.
24. As a developer, I want `lvh-yard catalog`, so that I get the Catalog view in a terminal.
25. As a developer, I want `lvh-yard catalog` to group Services by Workstation, so that it reads like `lvh-yard list`.
26. As a developer, I want one line for each Service with state, hostname and count, so that the output stays short.
27. As a developer, I want `--workstation <name>` on `lvh-yard catalog`, so that I see one Workstation.
28. As a developer, I want `--state <state>` on `lvh-yard catalog`, so that I see, for example, only Degraded Services.
29. As a developer, I want one line for an unreachable Workstation, so that I know why its Services are missing.
30. As a developer, I want no Service Warnings in `lvh-yard catalog`, so that each Service stays one line.
31. As a developer, I want `lvh-yard list` to keep its Instance lines, IDs and Service Warnings, so that `lvh-yard remove --id` still works.
32. As a developer, I want `lvh-yard list` to show the Degraded state, so that both commands agree.
33. As an API client, I want `degraded` as a state value, so that I get the same state as the dashboard.
34. As an API client, I want the container name, Container Port and Host Port of a Bound Instance, so that I can show them too.

## Implementation Decisions

### Degraded state

- `ServiceState` gets `DEGRADED`, with the JSON value `degraded`. The order of the values is Unpublished, Unavailable, Unhealthy, Degraded, Healthy.
- `ServiceState.of` keeps Unpublished and Unavailable as they are. With Instances, no passing Instance gives Unhealthy, every Instance passing gives Healthy, and anything else gives Degraded.
- Warning and critical both count as not passing. This matches `Routing`, which uses only passing Instances.
- The state uses every Instance of the Service. It does not look at the Instance Priority or at `outOfRange`.
- The Catalog State filter, the `state` query value and the OpenAPI enum get the new value from the enum.
- The Service page alerts do not change. A Degraded Service gets no red alert, because its hostname works.

### Badges

- The stylesheet adds a badge variant in the existing `--risk` orange. It does not use the primary amber, so that a badge does not look like a button.
- `ServiceViews.state` gives Degraded this variant.
- `InstanceViews` gives warning Health this variant in place of `secondary`.

### Catalog

- `CatalogPage` renders one row for each Service. The columns are Service, State, Hostname and Instances.
- The Instances cell says "N of M passing". With no Instances, it says "No Instances".
- The Instance and Health columns go away.
- Filters, the 5-second refresh and the Workstation headings do not change.

### Instance fields and the Instances card

- A Bound Instance stores its container name and Container Port in its Consul meta, next to the Binding that it stores today.
- `Instance` in the API gets `container`, `containerPort` and `hostPort`. All three are left out of the JSON for a Static Instance. `hostPort` is also left out for a Bound Instance with no Host Port. A container on the host network has no port mapping and no address of its own. Its `hostPort` is its Container Port, because its Bound Instance is at the Workstation's address and that port.
- The Instances card columns are Instance (the address), Container, Container Port, Host Port, Health, then the actions.
- For a Static Instance, the three container cells stay empty. For a Bound Instance with no Host Port, the Host Port cell says "Not published".
- A Remote Service shows the same columns, from the meta of its Home Workstation.

### Container Bindings card

- Each row keeps Follows, Container port and the actions.
- The Bound Instances cell becomes a summary: "N containers, P passing", with "1 container" for one.
- With no match, the cell keeps today's messages: "No container of this name runs." and "No container of this Compose service runs."

### lvh-yard catalog

- A new top-level command `catalog`, next to `list`. Its description is "Lists the Services of every Workstation, one line each."
- It reads the same API as `lvh-yard list` and applies the filters in the CLI.
- `--workstation <name>` keeps one Workstation. `--state <state>` keeps one state, and an unknown value is refused with the list of states.
- Output: the Workstation heading as in `lvh-yard list`, then one line for each Service with name, state, hostname and count, in aligned columns. An Unpublished Service has an empty hostname column.
- An unreachable Workstation gives `<name>: Workstation unreachable`, as in `lvh-yard list`.
- It prints no Service Warnings and no Instance IDs.
- `lvh-yard list` does not change, apart from the new state value.

## Testing Decisions

- A good test acts as a user or an API client. It looks only at what they can see: API answers, the HTML of a page, and CLI output.
- Seam 1 is the dashboard test, through `DashboardTest`, with a real Consul and Docker from Testcontainers.
- Seam 1 covers the state. One passing and one critical Instance give `degraded`. One passing and one warning Instance give `degraded`. All passing give `healthy`, and none passing give `unhealthy`. A Service where only the Static Instance passes, under the default Instance Priority, is `degraded`. An Unpublished Service with a failing Instance stays `unpublished`.
- Seam 1 covers the API fields `container`, `containerPort` and `hostPort`. It tests a Bound Instance with a mapped Host Port, one on the host network, and one with no Host Port. A Static Instance has none of them. The OpenAPI document shows them and the `degraded` value.
- Seam 1 covers the Catalog: one row for each Service, the count text, "No Instances", the Degraded filter, and the orange badge variant.
- Seam 1 covers the Service page: the Container Bindings summary for a Compose service with several containers, and the new Instances card columns with "Not published".
- Seam 2 is the CLI test `YardTest` with its stub dashboard. It covers `lvh-yard catalog` with and without filters, an unreachable Workstation, an unknown state, and the Degraded state in `lvh-yard list`.
- Prior art: `CatalogPageTest`, `ServicePageTest`, `ContainerBindingsTest`, `OpenApiTest`, and the CLI's `YardTest`.

## Out of Scope

- A Service Warning or a red alert for a Degraded Service.
- A state that follows the Instance Priority or the Out-of-range mark.
- Changes to the lines of `lvh-yard list`, apart from the state value.
- A separate table for containers on the Service page.
- The `EXPOSE` ports of an image.

## Further Notes

- LVH Yard is not released yet, so the new state value and the new Instance fields need no compatibility code. If a Binding registers its Bound Instances again, they get the new meta.
- Docker calls a port in an image's `EXPOSE` line "exposed". That is not a Host Port, so no text uses the word "exposed".
