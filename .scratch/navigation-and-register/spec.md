# Navigation bar, and two kinds of register

Status: done

Words follow `GLOSSARY.md`. This spec replaces the Import of `.scratch/docker-import/spec.md`.

## Problem Statement

The dashboard has no navigation. The Catalog page has three buttons to Containers, Aliases and Register, and the other pages have no way back except the header title.

One Register page mixes two different things: a Static Instance, and a Container Binding. The form changes its fields by a choice at the top. The CLI does the same: `lvh-yard register` takes static options and container options, and refuses a mix of them.

The Containers page has a third way to bind a container: Import. Import and `lvh-yard register --container` look the same, but they act differently. Import needs a running container that no Binding follows. It guesses the Binding kind, the port and the Service name. It publishes a new Service. `register --container` needs none of this, guesses nothing and never publishes. The user wants the word "import" for a later feature: export and import of Services in YAML files.

The good states have the dark primary badge: passing Health, a Healthy Service, an OK Status. They do not stand out from the other badges.

## Solution

Every page has a navigation bar in the header, with Catalog, Containers, Aliases and Static Instances. The link of the current page is highlighted. The Catalog page loses its three buttons.

The Register page becomes the Static Instances page at `/static-instances`. It lists this Workstation's Static Instances and has a form to register one. `/register` redirects to it.

The Containers page holds everything about containers. It keeps the list of Unfollowed Containers with a one-click Register button for each row. Below the list, a new form binds a Service to a container name or to a Compose service, which does not need to run.

The CLI has two subcommands:

```
lvh-yard register static <service> --port 8080 [--host ...] [--path ...] [--no-publish]
lvh-yard register container <container> [--port 80] [--service web] [--no-publish]
lvh-yard register container --compose shop/web --port 80 [--service web] [--no-publish]
```

`lvh-yard import` and the bare `lvh-yard register <service>` go away.

Both kinds publish a new Service with Local Scope, unless the user opts out. A Service that exists keeps its Publication, or stays Unpublished.

A container or a Compose service that a Container Binding of this Workstation already follows is refused.

The good states use a new green `success` badge.

## User Stories

1. As a user, I want a navigation bar on every page, so that I reach each list in one click.
2. As a user, I want the link of the current page highlighted, so that I know where I am.
3. As a user, I want the Catalog page without its three buttons, so that it shows only Services.
4. As a user, I want a page that lists my Static Instances, so that I see them in one place.
5. As a user, I want each listed Static Instance to link to its edit form, so that I change it from the list.
6. As a user, I want a form on that page that registers a Static Instance, so that I need no kind choice.
7. As a user with an old bookmark, I want `/register` to redirect to the new page, so that the bookmark works.
8. As a user, I want a "Publish a new Service" checkbox in that form, so that a new Service gets a hostname.
9. As a user, I want that checkbox on by default, so that one click is enough.
10. As a user, I want to clear that checkbox, so that a new Service stays Unpublished.
11. As a user, I want an existing Service to keep its Publication, so that a registration never changes a hostname.
12. As a user, I want `lvh-yard register static`, so that the command says what it adds.
13. As a user, I want `register static` to take all of the old static options, so that I lose nothing.
14. As a user, I want `register static --no-publish`, so that a new Service stays Unpublished.
15. As a user, I want `register container shop-web-1` to act as Import did, so that one short command is enough.
16. As a user, I want a running Compose container bound by Compose project and service, so that the Binding outlives `docker compose up`.
17. As a user, I want the port guessed for a container with one port, so that I type less.
18. As a user, I want the ports listed in a refusal, so that I know what to pass to `--port`.
19. As a user, I want the Service name guessed from the Compose service or the container name, so that I type less.
20. As a user, I want `--service` to choose the Service name, so that I can bind into any Service.
21. As a user, I want to bind a stopped container by name, so that I can set up a Binding first.
22. As a user, I want a clear refusal for a stopped container without a port, so that I know what to add.
23. As a user, I want `register container --compose shop/web --port 80`, so that I can bind a Compose service before it starts.
24. As a user, I want `web` as the default Service name for `--compose shop/web`, so that I type less.
25. As a user, I want a refusal for a container that a Binding follows, so that one container never gets two Bindings.
26. As a user, I want that refusal to name the Service, so that I know where the container is.
27. As a user, I want the one-click button labeled "Register", so that the page and the CLI use one word.
28. As a user, I want a binding form on the Containers page, so that I can bind a container that does not run.
29. As a user, I want that form to choose "Container name" or "Compose service", so that it shows only the fields I need.
30. As a user, I want a "Publish a new Service" checkbox in that form, so that it acts like the CLI.
31. As a user, I want the reason of a failed binding next to the field, so that I can fix it.
32. As a user, I want `lvh-yard import` gone, so that "import" is free for YAML files later.
33. As a user, I want green badges for good states, so that good states stand out.
34. As a user in the dark theme, I want the same green badge, so that both themes agree.
35. As an API client, I want an optional `publish` field on both write endpoints, so that the API can publish a new Service.
36. As an API client, I want `POST .../containers/{container}/register`, so that the API and the CLI use one word.
37. As an API client, I want 409 for a Binding of a followed target, so that I can show the reason.
38. As a reader of the user guide, I want the new commands and pages in it, so that it matches LVH Yard.

## Implementation Decisions

- The header of the layout gets the navigation bar. It is a list of four links: Catalog (`/`), Containers (`/containers`), Aliases (`/aliases`), Static Instances (`/static-instances`). Each page tells the layout which link is current, and that link gets `aria-current="page"`. The Service page and the edit page have no current link.
- The Catalog page drops its Containers, Aliases and Register buttons.
- The Register page becomes the Static Instances page at `/static-instances`. `GET /register` answers 301 to `/static-instances`. The page lists this Workstation's Static Instances: Service, address, Health, and a link to the edit form. The form has the Static Instance fields and a "Publish a new Service" checkbox. It drops the kind choice and the container fields.
- `app.css` adds a badge variant `success`, with `bg-green-600 text-white` in both themes. Neither Tailwind nor Basecoat 1.1.0 has one. Passing Health, the Healthy state, the OK Status Part and a good Reachability result use it. Other badges keep their variants.
- `POST /api/workstation/services/{service}/instances` and `POST /api/workstation/services/{service}/container-bindings` take an optional `publish` boolean. If the Service did not exist before the call, the call publishes it with Local Scope. A `publish` of `false` stops this. The existence check runs before the write, as Import does today.
- `POST /api/workstation/services/{service}/container-bindings` refuses with 409 a Binding whose target a Binding of this Workstation already has. The target is the same container name, or the same Compose project and service, whatever the port. The answer names the Service that follows it.
- `POST /api/workstation/containers/{container}/import` becomes `POST /api/workstation/containers/{container}/register`, with the same body `{port, service, publish}`.
- When `{container}` runs, the register endpoint acts as Import does today. When it does not run, it binds by container name. Then `port` is required, and the Service name defaults to the cleaned container name. A missing port answers 400 with field `port`.
- The CLI `register` becomes a parent command with two subcommands, `static` and `container`. The bare form with a Service name goes away. `lvh-yard import` goes away.
- `register static` takes the static options of today's `register`, plus `--no-publish`.
- `register container <container>` calls the register endpoint. It takes `--port`, `--service` and `--no-publish`.
- `register container --compose <project>/<service>` calls the container-bindings endpoint. It needs `--port`. It takes `--service`, with the cleaned Compose service as the default, and `--no-publish`. A positional container together with `--compose` is refused.
- On the Containers page, the one-click button is labeled "Register" and posts to `/containers/{container}/register`. The binding form posts to a page path on the Containers page. On success, the form goes to the Service's page. On failure, the page answers 400 with the reason next to the field.
- `openapi.yaml` and `docs/user-guide.md` change to match. The glossary has no Import term.

## Testing Decisions

- A good test acts as a user or an API client. It looks only at what they can see: API answers, page text and CLI output. It never reads the private state of a module.
- The main seam is the dashboard's HTTP API in the current dashboard test setup, with a real Consul and Docker in Testcontainers.
- That seam covers the `publish` field on both endpoints and the 409 refusal of a followed target. It also covers the register endpoint for a running and a stopped container.
- The page tests cover the navigation bar, the Static Instances page and its redirect. They also cover the Containers page form, its one-click button and the `success` badge.
- The CLI seam is the current CLI test with its stub dashboard. It covers `register static`, both forms of `register container` and their refusals.
- Prior art: `StaticInstancesTest`, `ContainerBindingsTest`, `ContainersTest`, `RegisterPageTest`, `CatalogPageTest`, `ServicePageTest`, `StatusTest`, `OpenApiTest` and the CLI's `YardTest`.
- `RegisterPageTest` becomes the test of the Static Instances page. The Import tests in `ContainersTest` move to the register endpoint.

## Out of Scope

- Export and import of Services in YAML files.
- A page that lists Container Bindings by themselves.
- A green or new color for warning or critical badges.
- A hidden alias for the bare `lvh-yard register` or for `lvh-yard import`.

## Further Notes

- `.scratch/docker-import/` stays as history of Import.
- LVH Yard has no release yet, so the CLI and API changes need no upgrade step.
