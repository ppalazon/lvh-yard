# A guide site and a landing page for the first public release

Status: ready-for-human

## Problem Statement

LVH Yard works, and the next release is `v0.2.0`. A new reader finds three things today: a landing page that describes features, a README, and `docs/user-guide.md`, a reference of about 130 KB that an AI wrote. No page starts from a pain of a developer. No page says that the Base Domain is the first choice that `lvh-yard ws init` asks for. No page shows how to reach a NAS, how to keep a Compose service under one name, how to test HTTPS with a Workstation CA, or how Consul and Traefik fit together.

## Solution

Build two sites from this repository, and publish them together at `https://lvhy.cc/`:

- The **landing page** at `/`. It is one static page. It names the pains, shows the product, and links to the guide.
- The **guide** at `/docs/`. Zensical builds it. It is written in the order of the reader's work: start, recipes, concepts, advanced, reference. Each recipe starts from one pain and has one diagram and one set of numbered steps.

`docs/user-guide.md` stays as it is. It is the AI-written reference of the codebase. The guide is a separate text. A human leads its writing, and AI helps. No page is generated in bulk, and no page goes live before the maintainer approves it.

## Decisions

These come from the grilling session of 2026-11-21. Do not reopen them without the maintainer.

### Layout

```text
docs/landing/        index.html, site.css, assets/        moved from docs/site/
docs/guide/          the Zensical Markdown sources        new
dev/install.sh       moved from docs/site/
dev/install.ps1      moved from docs/site/
zensical.toml        at the repository root: docs_dir = "docs/guide", site_dir = "dist/pages/docs"
dist/pages/          the landing page, and the install scripts. Git ignores /dist/.
dist/pages/docs/     the guide
```

`docs/` keeps its original documents: `user-guide.md`, `design.md`, `development.md`, `windows-test-vm.md`, `adr/` and `agents/`. It adds only `landing/` and `guide/`.

### Mise tasks

| Task                  | What it does                                                                                                       |
| --------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `pages:landing-build` | Builds `docs/landing` into `dist/pages/`. Copies `dev/install.sh`, `dev/install.ps1` and the dashboard `theme.js`. |
| `pages:docs-build`    | Runs `zensical build --strict` into `dist/pages/docs/`.                                                            |
| `pages:build`         | Runs `pages:landing-build`, then `pages:docs-build`.                                                               |
| `pages:landing-serve` | Builds the landing page and serves `dist/pages/`.                                                                  |
| `pages:docs-serve`    | Runs `zensical serve`, with drafts.                                                                                |

`pages:landing-build` removes only the files that it owns, so it never deletes `dist/pages/docs/`. Zensical runs from a version that `mise.toml` pins through `uv`. `.github/workflows/pages.yml` runs `pages:build`, uploads `dist/pages`, and watches `docs/landing/**`, `docs/guide/**`, `zensical.toml`, `dev/install.*` and the dashboard `theme.js`.

These files name `docs/site/` and change with the move: `.github/workflows/pages.yml`, `.gitignore`, `mise.toml`, `README.md` (the screenshot images), `docs/development.md`, `dev/test-windows-native.ps1` (a header comment), `cli/internal/update/update.go` (a comment), `CODING_STANDARDS.md`, `docs/landing/site.css` (a comment) and `dashboard/src/main/css/theme.css` (a comment). Files in `.scratch/windows-11/evidence/` are history. Leave them.

### The writing rules

- Write in English, in two stages. The maintainer chose this process on 2026-11-22.
- Stage 1, draft: use the `technical-writing` skill. State the checkpoint first: kind, audience, purpose and non-goals. The skill sets the structure: the answer first at every level, one home for each fact, noun-phrase headings (or the verbatim error string for a troubleshooting entry), and no banned constructions. A procedure also follows `writing-runbooks`. Finish stage 1 with the checklist of `reviewing-technical-prose`.
- Stage 2, polish: use the `ste80-humanizer` skill on the stage 1 text, in Humanize mode (Pass 1 fully, Pass 2 at 80%). It sets the sentences: active voice, simple tenses, no semicolons, and 25 words at most. The polish changes sentences only. Headings, the order of sections, tables, code blocks, diagrams, links, commands and outputs stay as they are, and no fact is added or lost. The page must pass `ste_check.py` with no findings.
- The writer saves the stage 1 text in `.scratch/guide-site/drafts/<slug>.stage1.md`, which Git ignores, so the maintainer can ask for a diff. The writer deletes the file when the maintainer approves the page.
- A page carries no dates and no statement about what was tested or where: the ticket of the page records the tests. A warning comes before the command it protects.
- A page shows the output of a command only when the reader needs it to follow the guide: a value to copy, a state to check, or a failure to recognize. Otherwise the text says what the command does, and the command stands alone. A page does not explain the columns of a table or of a list that the reader can read. The maintainer set this rule on 2026-11-22. The writer still runs every command and keeps the real outputs in the ticket notes.
- Use the terms of `GLOSSARY.md`, and only those. Write Workstation, not host. Write Workstation CA, not private CA. Write Container Binding, Instance Priority and Static Instance by those names.
- The guide may use two extra words for the Base Domain: **shared domain** (someone else runs its DNS, such as `lvh.me`) and **own domain** (you run its DNS). They do not go into `GLOSSARY.md`.
- Code, commands, paths and identifiers stay exactly as they are.
- Diagrams are Mermaid blocks in the guide. The landing page keeps hand-made SVG. Screenshots show the dashboard only. If Mermaid cannot draw what a page needs, the writer tells the maintainer what fails before any SVG diagram goes into the guide. A sequence diagram colors its boxes with `box rgba(33,118,255,0.25) Label` (blue), `rgba(34,170,80,0.25)` (green) or `rgba(245,158,11,0.25)` (orange), and `docs/guide/javascripts/mermaid-box-colors.js` makes the theme show them. The theme loads Mermaid from `unpkg.com`, so a diagram needs internet access.
- The header of the guide has a Base Domain field (`docs/guide/javascripts/base-domain.js`). A page writes an example that uses the reader's Base Domain with `lvh.me` or with `<Base Domain>` inside inline code or a code block, and the script replaces it. Without the script, the page shows `lvh.me`. An address that the reader can open is inline code with the whole address, such as `http://yard.<Base Domain>`, and the script makes it a link. An address with a fixed host, such as `http://127.0.0.1:7780`, is also a Markdown link, so it works without the script. A page that uses `lvh.me` for another meaning, such as the shared-domain examples of "Pick your Base Domain", has the comment `<!-- base-domain: off -->`. A Mermaid diagram keeps its text.
- The command names in this spec come from `docs/user-guide.md`. Run `lvh-yard <command> --help` to confirm each one before you write it into a page.

### The reader and the six pains

The reader is a developer who runs services on one to three computers, such as a laptop and a HomeLab box or a NAS. They work alone or in a small team on one LAN. They know Docker and the shell. They do not know Consul or Traefik, and no page except the Advanced page asks them to.

1. "I forget which port each service uses."
2. "Docker gives my container a new port after every `compose up`."
3. "I edit `/etc/hosts` on each computer, and I do it again for the next one."
4. "`localhost:3000` breaks my cookies and my OAuth callbacks."
5. "I need HTTPS in development, and I do not want to click through warnings."
6. "My teammate cannot open my service."

The six pains are the six cards of the landing section "Problems it solves".

### The navigation tree

```text
Start        What it solves · Install · Pick your Base Domain · Your first Service
Recipes
  Reach other computers   1 · 11 · 17
  Name your services      2 · 7 · 8 · 16 · 18
  Choose which Instance   6 · 9 · 10
  HTTPS and access        3 · 19 · 12 · 13 · 20
  Find and fix problems   14
  Move a setup            15
Concepts     How it works · The Yard · Instance Sources and Priority · Scope · Vocabulary
Advanced     How the Workstation Folder and compose.yaml wire Consul, Traefik and the dashboard
Reference    CLI cheat sheet · Settings and ports · Troubleshooting · Links to GLOSSARY.md and user-guide.md
```

The Reference links to `GLOSSARY.md` and `docs/user-guide.md` on GitHub. It does not copy them. The Vocabulary page is short, in the guide's own voice, and links to the full glossary.

### The recipe catalogue

Numbers are the catalogue numbers that the tickets use. Ticket numbers differ.

| #   | Recipe                                                                                                       | Group                              |
| --- | ------------------------------------------------------------------------------------------------------------ | ---------------------------------- |
| 1   | Reach another computer: a second Workstation, a Workstation with Traefik off, a Static Instance on a LAN box | Reach other computers              |
| 2   | Name every HomeLab service: process, container, NAS                                                          | Name your services                 |
| 3   | Test HTTPS with a Workstation CA (browser trust, Service Certificate, mutual TLS)                            | HTTPS and access                   |
| 4   | Moved into the Advanced page (Consul and Traefik by hand)                                                    | Advanced                           |
| 5   | Publish and bind Docker with the CLI                                                                         | Name your services (in 2, 7 and 8) |
| 6   | Instance Priority: your process first, the Docker image as fallback                                          | Choose which Instance              |
| 7   | One service as a process on the Workstation while the rest of the Compose stack stays in Docker              | Name your services                 |
| 8   | Compose ports that change on every `compose up`                                                              | Name your services                 |
| 9   | Replicas, health checks and failover                                                                         | Choose which Instance              |
| 10  | Swap a backend with an Alias                                                                                 | Choose which Instance              |
| 11  | Show your work on the LAN: Yard Scope and a teammate                                                         | Reach other computers              |
| 12  | Protect a dev service with Route Profiles: basic auth, an IP allow list, CORS                                | HTTPS and access                   |
| 13  | Require a Client Certificate for an internal tool                                                            | HTTPS and access                   |
| 14  | Find and fix a bad Route                                                                                     | Find and fix problems              |
| 15  | Move or copy a setup with a Backup                                                                           | Move a setup                       |
| 16  | Cookies, OAuth callbacks and subdomains that need a real hostname                                            | Name your services                 |
| 17  | A Windows laptop and a Linux server in one Yard                                                              | Reach other computers              |
| 18  | Your DNS blocks the Base Domain                                                                              | Name your services                 |
| 19  | A real HTTPS certificate on your own domain                                                                  | HTTPS and access                   |
| 20  | Open the dashboard to your LAN with a password                                                               | HTTPS and access                   |

Recipe 5 has no page of its own. Its commands appear in recipes 2, 7 and 8. Recipe 4 became the Advanced page.

### The recipe template

Every recipe page has these parts, in this order:

1. The pain, in one sentence, in the reader's words.
2. The result, in one sentence.
3. One diagram of the workflow (Mermaid).
4. Before you start: what must be true.
5. Numbered steps. Each step has one command. The CLI comes first. PowerShell is a tab only where the command differs. After the steps, a section "The same steps in the dashboard" is a short bullet list with no introduction. It names the address (`http://yard.<Base Domain>` or `http://127.0.0.1:7780`), the page and the buttons to use, and ends with "Follow the forms". The name of each page, button, switch and field of the dashboard is inline code, such as `Create Service`. It does not list the fields or repeat the steps. A recipe whose task has no form in the dashboard, or where the dashboard is clearly easier (Route Profiles, the Route Diagram), says so in its own words. Every page that has CLI steps for Services has this section. The page "Your first Service" has it too. A table has few words: a value or a short phrase in each cell.
   A step that publishes a Service ends with the hostname as inline code, such as `http://mailpit.lvh.me`, and tells the reader to open it in the browser. The Base Domain script makes it a link.
6. Check: one command that proves it works, with its real output.
7. Undo: the command that removes what the steps made.
8. If it fails: the Service Warning or the `lvh-yard test` result that names the cause.

### The Base Domain

The Base Domain is the first choice of `lvh-yard ws init`, and it has no default. For development, it must be a wildcard DNS that answers `127.0.0.1` for every name below it. Each Workstation picks its own. The Start page "Pick your Base Domain" teaches it. It has a diagram: the browser asks DNS, DNS answers `127.0.0.1`, local Traefik routes the request to the Instance, and the Yard Scope path `grafana.ws-b.<Base Domain>`.

The options, in this order:

1. **Your own wildcard domain.** At your registrar: `*.dev.example.com A 127.0.0.1`. You control the record, so nobody can change or remove it. This is the choice for daily work.
2. **A wildcard in your own resolver.** For example `address=/lab.home/127.0.0.1` in dnsmasq, Pi-hole or your router. It works offline. It also works when your router blocks public answers that point to loopback.
3. **A shared domain.** `l.lvhy.cc` belongs to this project and the project's examples use it. `lvh.me` belongs to a third party. Use either one for the first five minutes and for trials. Both can change or disappear at any time, and `l.lvhy.cc` has no promise to stay.
4. **`localhost`.** Use it offline. It needs no DNS. See the open check about IPv6 below.

The approved warning, for an admonition on that page:

> **A shared domain can change or disappear at any time.** The owner of `lvh.me` or of `l.lvhy.cc` controls its DNS. The owner can point the names to another address, or remove them. If that happens, your browser sends the requests for your Services to that address. Plain HTTP requests carry your cookies and your basic-auth passwords. If the names disappear, every Route stops working until you change the Base Domain. A Workstation CA certificate does not stop the first case for HTTP Routes. For daily work, use a domain that you control.

The page also shows how to change it later: `lvh-yard ws set base-domain <domain>`, then `lvh-yard ws compose recreate`.

### Production and the dashboard login

LVH Yard is a development tool for a network that you trust (`SECURITY.md`). It could run on a public server. The maintainer has not tested that, and no recipe covers it.

The approved text, for the landing block "Use it on a network that you trust" and for the section "Run on a public server (not tested)" of the Advanced page:

> **The dashboard has no login. You can add one.** Publish the `yard` Service with a Route Profile that has basic auth. Keep the dashboard on `127.0.0.1`. Let only Traefik listen on the address that others reach, and serve it over HTTPS. Use a domain that you own and that points to that address: `lvh.me` and `l.lvhy.cc` point to `127.0.0.1`. Anything that runs on the Workstation still reaches the dashboard without a password. The maintainer has not tested a public server.

Facts behind it, checked against the repo on 2026-11-21:

- After the first setup, `yard` is an ordinary Service (ADR-0006), so a Route Profile applies to it.
- `YARD_DASHBOARD_BIND_ADDRESS=0.0.0.0` bypasses the Route and its password.
- A Yard Route to `yard` does not work for other Workstations: its Instance is on loopback, and they mark it as an Out-of-range Instance.
- Consul ACLs are off in the reference setup. The dashboard and Traefik listen on `127.0.0.1` unless `.env` says otherwise.
- Recipe 20 proves the local part with `curl` and a `Host` header or `--resolve`: `401` without the password, `200` with it.

### The landing page

Keep the hero, the Yard diagram, the screenshots and the existing sections. Change these:

1. Add "Docs" to the header navigation.
2. Add a section "Problems it solves" after "How it works": six cards in the form pain, one command, link to the recipe. A link is live only when its page is live.
3. Add one SVG that shows Instance Priority: a process on the Workstation first, a Docker container as fallback.
4. Add a block "Pick your Base Domain", and after it a block of typical questions. Each answer has two sentences at most and links to a guide page when one exists:
   1. Why not edit `/etc/hosts`?
   2. Why not just use `localhost:3000`?
   3. Is it magic? How does `grafana.lvh.me` reach my computer?
   4. Does LVH Yard start or stop my processes? (No. It manages Services, not the processes behind them.)
   5. Will `lvh.me` stop working one day? (It can. Use your own domain.)
   6. Does it work offline? (Yes, with `localhost` or a wildcard in your own resolver.)
   7. Is it safe on my LAN? (Read the limits: the dashboard has no login.)
   8. Does it work on macOS and Windows? (Yes, with the limits that "What you need" lists.)
5. Add the dashboard-login text above to the block "Use it on a network that you trust".
6. Change both "Read the user guide" links to `/docs/`.
7. Tighten all the copy with the writing rules.

The Start page "What it solves" also has a short list, "What LVH Yard does not do": it does not start processes, it is not a DNS server, and it is not for production. The list repeats `SECURITY.md` and the glossary, and adds no new claim.

### Accuracy

`docs/user-guide.md` stays the maintained reference. The guide must not drift from it. `CODING_STANDARDS.md` gets one line, and `.github/pull_request_template.md` gets one checkbox: "If the change alters a command or a result that a page of `docs/guide/` shows, update that page." No doc-test automation. The strict build catches broken links. The writer of each recipe runs it on `mise run dev:up` and pastes the real output.

### Publishing

The first release of the guide is `v0.2.0`. The site goes live when this core is approved:

- the plumbing and the landing page;
- the Home page and the Start section (four pages);
- the Advanced page;
- recipes 1, 2, 3, 8, 11, 16 and 19, which cover the six pains (pains 1 and 2 are "Your first Service" and recipe 8).

Every page that the maintainer has not approved stays in `draft_docs` of `zensical.toml`, so the strict build leaves it out. A page that the maintainer approves leaves `draft_docs` and goes live, whether it is core or not. Landing cards link only to live pages. `v0.2.0` waits for the core. The tag `v0.2.0-rc.1` exists already.

### Open checks while writing

- `*.localhost` resolves to `::1` on this Linux computer, and Traefik listens on `127.0.0.1`. Test whether browsers and `curl` fall back to IPv4 before the Base Domain page says that `localhost` works. Test it on macOS and Windows as well, or label it untested there.
- A Route answers for the hostname `<service>.<Base Domain>` only. Test whether tenant subdomains such as `acme.web.<Base Domain>` can work. If they cannot, recipe 16 drops them and says so.
- Test that a basic-auth Route Profile on `yard` works over HTTP and HTTPS, and whether a plain-HTTP request leaks the password. Recipe 20 states the result.
- Public certificate issuance (recipe 19) is not tested by the maintainer. The page says so until someone runs it.
- Recipes 1 (a box on the real LAN) and 17 need real machines. Check them on `dev:up` first, and label the rest "tested by hand".

## Out of scope

- Any change to `docs/user-guide.md`.
- Documenting Traefik's Consul Catalog provider. LVH Yard does not use it (ADR-0002).
- A hardening guide for a public server.
- Versioned docs. The site shows the latest release only, until 1.0.0.
- A language other than English.
- A device that cannot run a Workstation, such as a phone. A Workstation needs Docker with Compose, so the guide makes no promise for such a device. The maintainer wants to think about it later.

## Tickets

Tickets are in `issues/`. Each page is one ticket. Every ticket is `ready-for-human`, because the maintainer leads the writing. Every ticket except 01 is blocked by 01. A ticket marked `Core: yes` blocks `v0.2.0`.
