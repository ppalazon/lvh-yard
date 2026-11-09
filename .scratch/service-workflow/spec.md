# Services that you create, with ordered Instance Sources

Status: ready-for-agent

Words follow `GLOSSARY.md`. ADR-0009 gives the routing rule, and it supersedes ADR-0005.

## Problem Statement

Today a Service has no record of its own. It exists only while it has an Instance, a Container Binding or a Publication. So you cannot create a Service first and fill it later. Each kind of Instance has its own page and its own form: the Static Instances page, the Containers page and the Aliases page. To set up one Service, you go to three places.

An Alias is a separate thing at the level of the Workstation. It has its own short hostname. It helps only the Service with the same name. A Service can use one Alias at most.

Instance Priority orders Instance kinds: Static, then Bound, then Alias. You cannot choose between two Static Instances, or between two Aliases to different Workstations.

## Solution

The catalog lists every Service of the Yard. It has a "New Service" button. You give only a name, and LVH Yard creates an empty, Unpublished Service. The Service stays until you delete it.

The Service page holds everything about one Service. You publish and unpublish it, choose its Scope and switch its access log. You attach Instance Sources: Static Instances, Container Bindings and Aliases. You put them in order and switch Instance Priority on or off. You apply Route Profiles, test Reachability, and delete the Service.

A Service can have many Instance Sources of each kind. With Instance Priority on, the Route uses the first source in the order that has a passing Instance. With it off, every passing Instance of every source shares the Route.

On a Remote Service row of the catalog, an "Alias" button links that Remote Service to a Service of this Workstation. If the Service does not exist, LVH Yard creates it and publishes it with Local Scope.

The Static Instances, Containers and Aliases pages go away.

## User Stories

In these stories, "user" is a person who uses one Workstation. "Teammate" is a user of another Workstation in the Yard.

### Create and delete Services

1. As a user, I want a "New Service" button in the catalog, so that I can create a Service before anything runs.
2. As a user, I want to give only a name, so that creating a Service is fast.
3. As a user, I want a new Service to start Unpublished and empty, so that nothing gets a hostname by mistake.
4. As a user, I want a name that is not one DNS label refused, so that the hostname always works.
5. As a user, I want a name that this Workstation already uses refused, so that I change no Service by mistake.
6. As a user, I want to land on the new Service's page, so that I can attach its Instance Sources at once.
7. As a user, I want an empty Service to stay in the catalog, so that I can come back to it later.
8. As a user, I want a "Delete Service" button at the bottom of the Service page, so that I can remove a Service.
9. As a user, I want a confirmation that names what a delete removes, so that I lose no setup by mistake.
10. As a user, I want a delete to remove the Publication and every Instance Source, so that nothing of the Service stays.
11. As a user, I want my containers to keep running after a delete, so that a delete changes only LVH Yard.
12. As a user, I want a deleted Service with Foreign Instances left to stay and say why.
13. As a user, I want a Service that only Foreign Instances give to work like any other Service, with no create step, so that I can publish and change what other tools register.

### Attach Static Instances

14. As a user, I want an "Add Static Instance" form on the Service page, so that I work in one place.
15. As a user, I want to edit a Static Instance from the Service page, so that I need no separate page.
16. As a user, I want an edited Static Instance to keep its place in the order, so that an edit leaves the Route alone.
17. As a user, I want many Static Instances in one Service, so that I can order two copies on different ports.
18. As a user, I want a Static Instance for a missing Service refused, so that a typing error creates no Service.

### Attach Container Bindings

19. As a user, I want a "Bind container" form on the Service page, so that I work in one place.
20. As a user, I want the container field to list Compose services as project and service, so that I can bind one with a click.
21. As a user, I want the container field to list containers with no Compose project by name, so that I can bind them too.
22. As a user, I want the list to hold stopped containers, so that I can bind a container that does not run now.
23. As a user, I want to type a container or a Compose service, so that I can bind what does not exist yet.
24. As a user, I want the port field to list the Container Ports of my pick, so that I need not look them up.
25. As a user, I want to type any port in the port field too, so that a port that is not listed still works.
26. As a user, I want a target that another Binding follows shown but not selectable, so that I see why I cannot bind it.
27. As a user, I want a typed target that another Binding follows refused at its field, so that I see what is wrong.
28. As a user, I want many Container Bindings in one Service, so that I can order two Compose services that give the same thing.
29. As a user, I want each Container Binding to show its Bound Instances, so that I see which containers it follows now.

### Attach Aliases

30. As a user, I want an "Alias" button on each Remote Service row with Yard Scope, so that I can use a teammate's Service.
31. As a user, I want the Alias form to offer my Services and accept a new name, so that I choose where the Alias goes.
32. As a user, I want the Remote Service's name as the default, so that the usual case needs one click.
33. As a user, I want a new name to create the Service with a Local Scope Publication, so that the hostname works at once.
34. As a user, I want an existing Service to keep its Publication, so that an Alias never changes my Scope.
35. As a user, I want an "Add Alias" form on the Service page, which lists Remote Services with Yard Scope.
36. As a user, I want many Aliases in one Service, so that I can fall back from one teammate to another.
37. As a user, I want a second Alias to the same Remote Service refused, so that the order has no repeats.
38. As a user, I want an Alias to a Service of this Workstation refused, so that a Service never routes to itself.
39. As a user, I want an Alias to stay after its target leaves Yard Scope or becomes Unreachable, so that it works again later.
40. As a user, I want a note on such an Alias that says why it gives no Instances, so that I understand the Route.
41. As a user, I want an Alias to show the Instances of its Remote Service, so that I see where it sends requests.

### Order the Instance Sources

42. As a user, I want the Instance Sources shown as one ordered list, so that I see the order that the Route follows.
43. As a user, I want up and down buttons on each Instance Source, so that I can change the order.
44. As a user, I want a new Instance Source at the end of the order, so that adding one never takes over the Route.
45. As a user, I want a new Foreign Instance at the end too, so that another tool never takes over my Route.
46. As a user, I want the Route to use the first source with a passing Instance, so that I reach my preferred copy.
47. As a user, I want the passing Instances of that source to share requests, so that a scaled Compose service spreads its load.
48. As a user, I want the Route to fall back to the next source, so that the hostname keeps working.
49. As a user, I want every Instance in range of every source used while none passes, Alias Instances included, so that the hostname still answers.
50. As a user, I want an Instance Priority toggle on the Service page, so that I can spread requests over every source.
51. As a user, while the toggle is off, I want every passing Instance of every source to share the Route, Aliases included.
52. As a user, while the toggle is off, I want the up and down buttons hidden and the order kept.
53. As a user, I want the toggle and the order kept over unpublish and publish, so that I set them up once.
54. As a user, I want the page to mark the source that the Route uses now, so that I see the result of the order.

### Other Workstations

55. As a teammate, I want my Route for a Yard Service to follow its Home Workstation's order, so that we reach the same copy.
56. As a teammate, I want my Workstation to skip another Workstation's Alias sources, so that my Route never depends on a third Workstation.
57. As a user, I want a risk Service Warning for an Alias above other sources in a Yard Service, so that I know teammates differ.

### Pages and navigation

58. As a user, I want the top menu to hold Catalog, Route Profiles, Backups and Status, so that the menu stays short.
59. As a user, I want the Static Instances, Containers and Aliases pages removed, so that I set up a Service in one place.
60. As a user, I want the Service page parts in a fixed order, so that I find each part fast.
61. As a user, I want the order to be summary, Publication, Instance Sources, Route Profiles, Reachability and Delete.
62. As a user, I want the Publication and Route Profiles parts on an Unpublished Service's page, so that I can publish from there.
63. As a user, I want a Remote Service page to show its ordered sources without buttons, so that I see its Route.

### CLI

64. As a user, I want `lvh-yard service create <name>` and `lvh-yard service delete <name>`, so that I can script a setup.
65. As a user, I want `register static`, `register container` and `register compose` to attach to an existing Service.
66. As a user, I want `register` on a missing Service to fail and name `lvh-yard service create`, so that I know the next step.
67. As a user, I want `lvh-yard alias add <service> --workstation <w> --service <s>` to act as the Alias button does.
68. As a user, I want `lvh-yard alias remove` and `lvh-yard alias list` to work per Service, so that I can see and remove one Alias.
69. As a user, I want `lvh-yard priority <service>` to print the sources in order with their IDs, so that I can name them.
70. As a user, I want `lvh-yard priority <service> <id>...` to set the order, so that I need no dashboard.
71. As a user, I want `lvh-yard priority <service> --on` and `--off` to switch the toggle, so that I need no dashboard.
72. As a user, I want `lvh-yard containers` to keep listing the Unfollowed Containers, so that I can find what to bind.
73. As a user, I want `lvh-yard catalog` to show empty Services, so that the CLI and the catalog page agree.

### Backups

74. As a user, I want a Backup to hold each Service, even an empty one, so that an import gives back every Service.
75. As a user, I want a Backup to hold the sources in order with the toggle, so that an import gives back the same Routes.
76. As a user, I want a Backup to leave Foreign Instances out of the order, so that it holds only what LVH Yard set up.
77. As a user, I want an import to refuse an Alias to the importing Workstation, as today, so that no Service routes to itself.

## Implementation Decisions

- A new Service record in this Workstation's Consul KV holds the order of its Instance Sources and the Instance Priority toggle. There is one key per Service.
- A Service exists while it has this record or a Foreign Instance. A Service that only Foreign Instances give is a normal Service: creating its name is refused, and the first change that needs a record writes one. That is publishing, attaching a source, setting the Instance Priority and adding an Alias. A missing name, with no record and no Instance, still answers 404.
- The Publication keeps its Scope, its Route Profiles and its access log. It loses the Instance Priority. Unpublishing deletes only the Publication.
- Each Instance Source has a stable ID inside its Service. A Static Instance and a Foreign Instance use their Consul ID. A Container Binding uses its Binding ID. An Alias uses `<workstation>/<service>` of its Remote Service. The order stores these IDs.
- At read time, a source that the order does not name goes to the end. An ID in the order whose source is gone is skipped. A write of the order drops IDs whose sources are gone.
- An Alias is stored with its Service. The Workstation-level Alias keys and the short hostnames of today go away.
- No code reads old Alias keys or old Publication values, because LVH Yard is not released yet.
- The Route choice becomes one rule over ordered sources. Its input is the ordered sources with their Instances, the toggle, and whether the Workstation that builds the Route is the Home Workstation.
- Out-of-range Instances never count in the Route choice. On any other Workstation, Alias sources are left out before the choice.
- With the toggle off, the passing Instances of every source share the Route. If none pass, every Instance in range of every source shares it, Alias Instances included.
- The routing result names the Instance Source that the Route uses now and the sources that wait. It replaces today's Instance kind. The API and the CLI show it.
- The InstanceKind order and its default go away. A source still has a kind (static, bound, alias or foreign) for display and for the Alias rule.
- The REST API gets these endpoints: create a Service, delete a Service, add and remove an Alias of a Service, and read and set the order and the toggle.
- A new Static Instance or Container Binding for a missing Service answers 404. Changes to a Remote Service still answer 405 with an explanation.
- The Workstation-level Aliases API goes away. The pages for Static Instances, Containers and Aliases go away.
- The containers API stays for the container list and for `lvh-yard containers`. Its one-click register endpoint goes away.
- The container list API returns every container of this Workstation, stopped ones included. Each entry has its Compose project and service, its Container Ports, and whether a Container Binding follows it.
- The Alias button and `lvh-yard alias add` publish a new Service with Local Scope. The "New Service" button does not publish.
- The catalog marks a Service with no Instance Sources as empty. An empty Service is Unpublished until you publish it. A published one with no Instances is Unavailable, as today.
- The risk Service Warning for Alias sources above other sources applies only to a Service of Yard Scope.
- The Backup format stores each Service with its order and toggle, and each Alias inside its Service. The format version goes up. An older file is refused, because LVH Yard is not released yet.
- `lvh-yard publish` loses `--priority`. `register` loses `--no-publish`, because it never creates a Service.

## Testing Decisions

- A good test acts as a user or an API client. It looks only at what they can see: API answers, page text and CLI output.
- The main seam is the component test: the dashboard over HTTP with the fake Consul and fake Docker.
- Component tests cover create and delete, each kind of Instance Source, the order and the toggle. They cover Aliases from the catalog and from the Service page. They cover the container list, the removed pages and Backups.
- Component tests cover each refusal: a bad name, a repeat name, a source for a missing Service and a followed target. They also cover a repeat Alias and an Alias to this Workstation.
- Component tests read a Yard Service from the remote Workstation fake. It follows the order and skips Alias sources.
- A unit test of the Route choice covers each edge case. Examples are a source with no Instances, Out-of-range Instances and all sources failing.
- The unit test also covers Alias sources skipped on another Workstation, the toggle off with and without passing Instances, and IDs whose sources are gone.
- A unit test of the Backup format covers the new Service entries, the order and the refused old version.
- The CLI test with its stub dashboard covers `service create`, `service delete`, `register` on a missing Service and `alias add`. It also covers `alias remove`, `alias list` and `priority` with its output.
- One integration test, `InstancePriorityIT`, uses a real Traefik. It shows that the Route moves to the second Static Instance after the first one stops.
- The OpenAPI test covers the new and removed endpoints.
- Prior art for component tests: `InstancePriorityComponentTest`, `ServicePageComponentTest`, `AliasesComponentTest`, `CatalogPageComponentTest` and `RemoteServicesComponentTest`.
- More prior art: `ContainerBindingsComponentTest`, `BackupExportComponentTest`, `BackupImportComponentTest`, `NavigationBarComponentTest` and `OpenApiComponentTest`.
- Prior art for unit tests: `RoutingTest`, `BackupFormatTest` and the CLI's `YardTest`.

## Out of Scope

- Reachability tests run by another Workstation.
- Renaming a Service. You create a new Service and delete the old one.
- An order of single Instances inside one Container Binding or one Alias.
- Weights for Instance Sources.
- An Alias to a Remote Service with Local Scope.
- Moving an Instance Source from one Service to another.
- Redirects from the old page URLs, and code that reads old Alias keys, old Publications or old Backups.

## Further Notes

- `docs/user-guide.md` must describe the new flow: create a Service, attach sources, order them, and add an Alias from the catalog. Its CLI part must match the new commands.
- `GLOSSARY.md` already has the new entries for Service, Instance Source, Alias and Instance Priority.
- A switch of the Route still takes up to about 15 seconds: one Consul check interval plus one Traefik poll.
