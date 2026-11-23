# Outline of the guide

For the maintainer to edit. Nothing here is a page yet. Every page below is a ticket in `issues/`. Change a title, a path, an order or a pain, and I update the ticket.

Rules for the titles: sentence case, in the words of the reader, and no recipe numbers. The path is the file in `docs/guide/`. The URL is `https://lvhy.cc/docs/<path without .md>/`.

## Start

| #   | Title                  | Path                             | The reader's need                                                   | Diagram                                      | Verified on                | Core |
| --- | ---------------------- | -------------------------------- | ------------------------------------------------------------------- | -------------------------------------------- | -------------------------- | ---- |
| 37  | Home (a short welcome) | `index.md`                       | The six pains in one list, and a link to Start.                     | None                                         | Claims checked in the docs | yes  |
| 04  | What LVH Yard solves   | `start/what-it-solves.md`        | "Will this fix my problem?" The six pains, and what it does not do. | Browser, wildcard DNS, Traefik, Instances    | Claims checked in the docs | yes  |
| 05  | Install LVH Yard       | `start/install.md`               | A working Workstation in five minutes.                              | What `ws init` writes and Docker starts      | Clean Linux user           | yes  |
| 06  | Pick your Base Domain  | `start/pick-your-base-domain.md` | "`ws init` asks for a Base Domain. What do I type?"                 | DNS answers `127.0.0.1`; Yard Scope hostname | `dev:up`, DNS lookups      | yes  |
| 07  | Your first Service     | `start/first-service.md`         | Pains 1 and 2: the first name works, for a process and a container. | Service, Instances, Publication              | `dev:up`                   | yes  |

## Recipes

### Reach other computers

| #   | Title                                   | Path                                 | Pain                                                  | Variants or parts                                                            | Verified on                     | Core |
| --- | --------------------------------------- | ------------------------------------ | ----------------------------------------------------- | ---------------------------------------------------------------------------- | ------------------------------- | ---- |
| 39  | Create a Yard and join it               | `recipes/reach/create-a-yard.md`     | "Each of my computers sees only its own Services."    | `ws-a` starts the Yard, `ws-b` joins it, in one list with Workstation badges | `ws` commands on two folders    | yes  |
| 08  | Reach a service on another computer     | `recipes/reach/another-computer.md`  | "A service runs on another box, and I want its name." | Yard Scope or an Alias. Traefik off. A Static Instance, no Yard.             | `ws` commands; real LAN by hand | yes  |
| 09  | Show your work to a teammate            | `recipes/reach/show-your-work.md`    | "My teammate cannot open my service."                 | Yard Scope across two Workstations                                           | `dev:up`                        | yes  |
| 10  | Use a Windows laptop and a Linux server | `recipes/reach/windows-and-linux.md` | "My laptop is Windows and my HomeLab box is Linux."   | Two Workstations, the federation ports                                       | Real Windows, by hand           | no   |

### Name your services

| #   | Title                                                            | Path                                       | Pain                                                             | Commands                                                     | Verified on              | Core |
| --- | ---------------------------------------------------------------- | ------------------------------------------ | ---------------------------------------------------------------- | ------------------------------------------------------------ | ------------------------ | ---- |
| 11  | Name every HomeLab service                                       | `recipes/name/homelab-services.md`         | "I forget the port of my NAS, media server and Grafana."         | `register static`, `register container`, `catalog --details` | `dev:up`                 | yes  |
| 12  | Develop one service on your Workstation, keep the rest in Docker | `recipes/name/host-process-and-compose.md` | "I run `web` with hot reload and the rest in Compose."           | `register compose`, `register static`, `priority`            | `dev:up`                 | no   |
| 13  | Stop chasing Compose ports                                       | `recipes/name/compose-ports.md`            | "Docker gives my container a new port after every `compose up`." | `containers`, `register compose`                             | `dev:up`                 | yes  |
| 14  | Fix cookies and OAuth callbacks                                  | `recipes/name/cookies-and-oauth.md`        | "`localhost:3000` breaks my cookies and my callback."            | `publish`, a callback URL, a cookie on the Base Domain       | `dev:up`, a small app    | yes  |
| 15  | Fix a Base Domain that your DNS blocks                           | `recipes/name/dns-blocks-base-domain.md`   | "`app.lvh.me` does not resolve here, or not offline."            | `ws set base-domain`, `ws compose recreate`, a resolver rule | `dev:up`; router by hand | no   |

### Choose which Instance answers

| #   | Title                                 | Path                                      | Pain                                                       | Commands                                  | Verified on | Core |
| --- | ------------------------------------- | ----------------------------------------- | ---------------------------------------------------------- | ----------------------------------------- | ----------- | ---- |
| 16  | Put your process before the container | `recipes/choose/instance-priority.md`     | "Use my dev process first, the container when it is down." | `priority`, `catalog --details`           | `dev:up`    | no   |
| 17  | Test replicas and failover            | `recipes/choose/replicas-and-failover.md` | "What does my frontend do when one backend dies?"          | `register static --path`, `test`          | `dev:up`    | no   |
| 18  | Swap a backend with an Alias          | `recipes/choose/swap-with-an-alias.md`    | "Use the `api` of a coworker's Workstation, then go back." | `alias add`, `alias list`, `alias remove` | `dev:up`    | no   |

### HTTPS and access

| #   | Title                                          | Path                                  | Pain                                                               | Commands                                                         | Verified on                                | Core |
| --- | ---------------------------------------------- | ------------------------------------- | ------------------------------------------------------------------ | ---------------------------------------------------------------- | ------------------------------------------ | ---- |
| 19  | Test HTTPS with your Workstation CA            | `recipes/https/workstation-ca.md`     | "I need HTTPS and I do not want browser warnings."                 | `ws tls https create`, `ws tls ca issue service`, `--mutual-tls` | `dev:up`; browsers by hand                 | yes  |
| 20  | Use a real certificate on your own domain      | `recipes/https/real-certificate.md`   | "I own a domain and I want HTTPS with no CA to install."           | `ws tls https import`                                            | `import` with a test CA; issuance untested | yes  |
| 21  | Protect a dev service with Route Profiles      | `recipes/https/route-profiles.md`     | "A password, an IP allow list or CORS, without touching the code." | `profiles set`, `publish --profile`                              | `dev:up`                                   | no   |
| 22  | Require a Client Certificate                   | `recipes/https/client-certificate.md` | "Only people with my certificate open this tool."                  | `ws tls client ...`, a `clientCertificate` profile               | `dev:up`; browsers by hand                 | no   |
| 23  | Open the dashboard to your LAN with a password | `recipes/https/dashboard-password.md` | "I want the dashboard from another computer, and it has no login." | A basic-auth profile on `yard`, `ws set traefik-bind-address`    | `dev:up`                                   | no   |

### Find and fix problems, and move a setup

| #   | Title                    | Path                       | Pain                                        | Commands                                                  | Verified on           | Core |
| --- | ------------------------ | -------------------------- | ------------------------------------------- | --------------------------------------------------------- | --------------------- | ---- |
| 24  | Find and fix a bad Route | `recipes/fix/bad-route.md` | "The hostname fails and I do not know why." | `catalog --details`, `test`, `--access-log`, `status`     | `dev:up`, four breaks | no   |
| 25  | Move or copy a setup     | `recipes/move/backup.md`   | "New laptop, or a teammate needs my setup." | `export`, `import --dry-run`, `import`, `import --update` | `dev:up`              | no   |

## Concepts

| #   | Title                              | Path                                        | Covers                                                          | Core |
| --- | ---------------------------------- | ------------------------------------------- | --------------------------------------------------------------- | ---- |
| 26  | How a request reaches your Service | `concepts/how-it-works.md`                  | DNS, Traefik, the Route, the Instance, in one sequence diagram. | no   |
| 27  | The Yard                           | `concepts/the-yard.md`                      | Peers, the Gossip Key, the Invitation, the Main CA.             | no   |
| 28  | Instance Sources and Priority      | `concepts/instance-sources-and-priority.md` | The four sources and the order of use.                          | no   |
| 29  | Scope                              | `concepts/scope.md`                         | Local and Yard Scope, and what Scope does not protect.          | no   |
| 30  | Vocabulary                         | `concepts/vocabulary.md`                    | The ten words, with a link to the glossary.                     | no   |

## Advanced

| #   | Title                                                              | Path                                 | Covers                                                                                                                                                                                                              | Core |
| --- | ------------------------------------------------------------------ | ------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---- |
| 31  | How the Workstation Folder wires Consul, Traefik and the dashboard | `advanced/compose-consul-traefik.md` | The files, the three containers, the HTTP provider and why not Consul tags, host networking, Consul and Traefik configuration, a Foreign Instance by hand, running without `ws init`, a public server (not tested). | yes  |

## Reference

| #   | Title              | Path                              | Covers                                                            | Core |
| --- | ------------------ | --------------------------------- | ----------------------------------------------------------------- | ---- |
| 32  | CLI cheat sheet    | `reference/cli.md`                | The commands, grouped by task.                                    | no   |
| 33  | Settings and ports | `reference/settings-and-ports.md` | `ws set` names, `.env` names, ports, what faces the LAN.          | no   |
| 34  | Troubleshooting    | `reference/troubleshooting.md`    | Symptom, cause, command, fix.                                     | no   |
| 35  | More documents     | `reference/links.md`              | Links to the glossary, the AI-written user guide, ADRs, security. | no   |

## Writing order

1. Ticket 06, Pick your Base Domain. It sets the voice, the diagram style and the admonition style.
2. Tickets 07, 04, 05 and 37, the rest of Start and the Home page.
3. The core recipes, in this order: 13, 11, 08, 14, 19, 20, 09.
4. Ticket 31, the Advanced page.
5. Ticket 02, the landing page, once the pages that its cards link to are approved.
6. The other recipes, then Concepts, then Reference.

## Decisions

1. The URLs have the group folders: `/docs/recipes/reach/another-computer/`. The navigation sections match the folders.
2. Pages 12 and 16 stay separate. Page 16 is the concept with the commands, and page 12 is the daily workflow.
3. The Home page `index.md` is a short welcome with the six pains and a link to Start. It is ticket 37. Ticket 04 stays on the idea and its limits.
