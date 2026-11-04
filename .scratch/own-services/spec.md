# LVH Yard sets up its own Services once

Status: done

Words follow `GLOSSARY.md`. The decision is in `docs/adr/0006-lvh-yard-sets-up-its-own-services-once.md`. This spec replaces the design in `.scratch/core-services/spec.md`.

## Problem Statement

Today LVH Yard keeps three Core Services on every Workstation: `yard`, `consul` and `traefik`. The user cannot remove them, unpublish them, give them Yard Scope or change their Instance Priority. Their names are taken, so no Service or Alias can use them. When the user removes one, the dashboard puts it back at its next start.

The Core Services also add a fourth Status Part. That part only repeats the checks of the Consul and Traefik parts. Those parts already use the addresses from the environment variables. Also, a Route to the dashboard always exists. The user cannot remove it.

The `consul` Core Service also mixes with Consul's own server entry. Consul keeps that entry in its catalog under the same name.

## Solution

LVH Yard has no Core Services. The setup marker is a key in Consul KV. When the dashboard starts and the marker does not exist, the dashboard registers three Services. It publishes each one with Local Scope:

- `yard`, the dashboard, at `yard.<base domain>`
- `consul-ui`, Consul's web page, at `consul-ui.<base domain>`
- `traefik-admin`, Traefik's admin page, at `traefik-admin.<base domain>`

The dashboard then writes the marker and never does this setup again. After the setup, the three Services are ordinary Services. The user can remove, unpublish or change them, as any other Service.

Status has three parts: Consul, Traefik and Docker. The dashboard always answers at its own address, such as `127.0.0.1:7780`. When the `yard` Service is published, Traefik gets a Route to the dashboard. When it is not, Traefik gets no such Route.

The name `consul` is refused for a Service and for an Alias, because it is Consul's own entry in its catalog. No other name is reserved.

## User Stories

1. As a new user, I want `yard.<base domain>` to open the dashboard, so that I use LVH Yard with no extra steps.
2. As a new user, I want `consul-ui.<base domain>` to open Consul's web page, so that I see the registry with no extra steps.
3. As a new user, I want `traefik-admin.<base domain>` to open Traefik's admin page, so that I see the proxy with no extra steps.
4. As a user, I want the three new Services to have Local Scope, so that teammates get no Routes to my loopback pages.
5. As a user, I want each of the three Services to have an HTTP check, so that its Health shows whether the page answers.
6. As a user, I want the `yard` Instance at the dashboard's address and port, so that its Route follows my bind address.
7. As a user, I want the `consul-ui` Instance on loopback at Consul's HTTP port, so that it follows `YARD_CONSUL_HTTP_PORT`.
8. As a user, I want the `traefik-admin` Instance on loopback at Traefik's admin port, so that it follows `YARD_TRAEFIK_ADMIN_PORT`.
9. As a user, I want to remove the `consul-ui` Service, so that I have no hostname that I never use.
10. As a user, I want a removed Service to stay removed after a restart, so that LVH Yard keeps my choice.
11. As a user, I want to unpublish the `yard` Service, so that no hostname goes to the dashboard.
12. As a user, I want to give `yard` Yard Scope, so that teammates can open my dashboard on my LAN address.
13. As a user, I want to put a Route Profile on `traefik-admin`, so that authentication protects the admin page.
14. As a user, I want to change the Instance Priority of the three Services, so that no Service has special rules.
15. As a user, I want to use the names `yard` and `traefik` for my own Services, so that LVH Yard takes no names from me.
16. As a user with my own `yard` Service, I want the first setup to leave it alone, so that it stays as it is.
17. As that same user, I want the setup to still write the other two Services, so that I get the free hostnames.
18. As a user, I want a Service named `consul` refused with a clear reason, so that my Instances never mix with Consul's own entry.
19. As a user, I want an Alias named `consul` refused with the same reason, so that one rule covers every name.
20. As a user, I want an Import that gives the name `consul` refused with that reason, so that the Containers page tells me why.
21. As a user, I want Consul's own `consul` entry hidden from the Catalog, so that I see only my Yard's Services.
22. As a user, I want the status page to show Consul, Traefik and Docker, so that I see the real processes.
23. As a user, I want the status to read Consul and Traefik at the configured addresses, so that it works without the three Services.
24. As a user, I want one header dot for each of the three Status Parts, so that the dots match the status page.
25. As a user, I want the help of `lvh-yard status` to say that it shows whether LVH Yard works, so that the help is true.
26. As a user, I want `lvh-yard status` to exit with 1 only for Consul, Traefik or Docker, so that a removed Service never fails my scripts.
27. As a user, I want the next steps of `lvh-yard init` to use no Core Service words, so that they use only words that exist.
28. As a user, I want `lvh-yard list` to show the three Services with no "(Core Service)" mark, so that they look ordinary.
29. As a user, I want the `yard` Service page to show the same controls as any Service, so that I can change it there.
30. As a user who unpublished `yard`, I want Traefik to have no Route for `yard.<base domain>`, so that the hostname is gone.
31. As a user who unpublished `yard`, I want to open the dashboard at its own address, so that I never lose LVH Yard.
32. As a user, I want a teammate's three Services shown as ordinary Remote Services, so that the Catalog has one kind of Service.
33. As a user with a slow Consul, I want the setup to run in the background, so that the dashboard starts at once.
34. As a user with a stopped Consul, I want the setup to try again, so that it runs once Consul is ready.
35. As a user, I want the setup to log its failure and its end once each, so that I see what happened.
36. As a user who deletes Consul's data, I want the next start to do the setup again, so that I get the Services.
37. As an API client, I want no `core` field on a Service or an Instance, so that the API has no removed concept.
38. As an API client, I want the API documentation to say nothing about Core Services, so that it matches the API.
39. As an API client, I want 400 and the reason for the name `consul`, so that I can show it to my user.
40. As a reader of the user guide, I want to learn that I can remove the three Services, so that I know my choices.

## Implementation Decisions

- The Core Service concept goes away from the dashboard, the CLI, the pages, the API and its documentation. Only the first-time setup knows the three names.
- A new first-time setup module replaces the Core Services module. As today, it runs in the background after the web server starts. While Consul does not answer, it tries again every 5 seconds. When it starts to fail, it logs one line. When it finishes, it logs one line.
- The setup marker is a key in this Workstation's Consul KV. Its value is the time of the setup. When the key exists, the setup does nothing.
- When the key does not exist, the setup writes each of the three Services that does not exist yet. Then it writes the key.
- If a Service has an Instance, a Container Binding or a Publication, the setup leaves it alone.
- The setup uses the same write paths as a user's request. It registers a Static Instance with an HTTP check. It publishes with Local Scope and the default Instance Priority. Its Instances get ordinary Static Instance IDs and no special mark.
- The three Services and their checks are as follows:
  - `yard`: the dashboard's bind address and port, with an HTTP check on `/actuator/health`. When the dashboard binds to `0.0.0.0`, the address is loopback.
  - `consul-ui`: loopback and Consul's HTTP port, with an HTTP check on `/v1/status/leader`.
  - `traefik-admin`: loopback and Traefik's admin port, with an HTTP check on `/ping`.
- The name rule refuses `consul` for a Service, an Alias and an Import. The answer is 400 with the reason "consul is Consul's own entry in its catalog". No other name is reserved.
- As today, the Yard reader hides Consul's own server entry by its ID.
- All refusals that protected a Core Service go away. These are removing, unpublishing, Yard Scope, Instance Priority and Route Profile changes. Their 409 answers go away from the API and its documentation.
- The Status Parts are Consul, Traefik and Docker. The Core Services part goes away. The status page text and the status API summary say whether LVH Yard works on this Workstation.
- The Traefik configuration has no fallback Route for the dashboard. A Route for `yard` exists only for a published `yard` Service with a routed Instance.
- The `core` field goes away from the Service and Instance JSON. The Core Service mark in Consul's service metadata goes away too.
- The CLI drops the "(Core Service)" mark in `lvh-yard list`.
- The help of `lvh-yard status` is "Shows whether LVH Yard works on this Workstation."
- The last next step of `lvh-yard init` is "Make sure that LVH Yard works: lvh-yard status".
- The Service page shows the same controls for every Service.
- The user guide changes the setup steps and every place that names Core Services. It names the three Services that the first start sets up. It says that the user can remove them.
- No upgrade step exists for Workstations with Core Services, because LVH Yard has no release yet.

## Testing Decisions

- A good test acts as a user or an API client. It looks only at what they can see: API answers, page text, the Traefik configuration and CLI output. It never reads the private state of a module.
- The main seam is the dashboard's HTTP API in the current dashboard test setup, with a real Consul in Testcontainers.
- That seam covers the three Services after the setup, with their Instances, checks and Local Scope. It also covers removing and changing them, the refusal of `consul`, the three Status Parts and the Traefik configuration.
- The "only once" stories use the remote Workstation of the tests. Its Consul starts empty in each test run, and the tests can restart its dashboard.
- One test removes `consul-ui`, restarts the dashboard and expects `consul-ui` to stay removed.
- One test stops the dashboard, deletes the marker and writes a `yard` Service straight into Consul. After the restart, it expects `yard` unchanged and the other two Services written.
- One test starts the dashboard while Consul does not answer. When Consul answers, the test expects the setup to finish.
- The test support waits for the marker key, in place of the "Core Services kept" key.
- The CLI seam is the current CLI test with its stub dashboard. It covers the text of `lvh-yard list`, `lvh-yard status` and the next steps of `lvh-yard init`.
- The page text uses the current page tests: the status page, the Service page, the Aliases page and the Containers page.
- Prior art: the Core Services test, the status test, the Traefik configuration test, the Aliases and Containers tests for name refusals, and the CLI test.
- The Core Services test becomes the test of the first-time setup. Tests that only check Core Service refusals go away.

## Out of Scope

- An upgrade step from Core Services to the new Services.
- A command or a page button that runs the setup again.
- A setup that asks the user which of the three Services to write.
- A setup for Workstations that do not run the dashboard.

## Further Notes

- The ADR `0006-lvh-yard-sets-up-its-own-services-once.md` records the rejected options. These are Core Services that the dashboard keeps on every start, and setup commands that `lvh-yard init` prints.
- `.scratch/core-services/` stays as history of the earlier design.
