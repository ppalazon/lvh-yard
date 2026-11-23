# 08: Recipe 1: Reach another computer

**What to build:** Write the recipe "Recipe 1: Reach another computer" with the recipe template of the spec.

**Blocked by:** 01

**Status:** done

**Core for v0.2.0:** yes

## Pain

"A service runs on another computer in my LAN, and I want to open it by name."

## Result

The reader opens the service from this computer at a hostname, with the right variant for what runs on the other box.

## Commands to use

- Variant A, a second Workstation joins the Yard: `lvh-yard ws yard share`, `lvh-yard ws yard join`, `lvh-yard publish <service> --scope yard`, `lvh-yard catalog`. Hostname: `<service>.<workstation>.<Base Domain>`.
- Variant B, a Workstation with Traefik off: `lvh-yard ws set traefik off` and the commands that `ws set` prints. Read the user guide section "A host without Traefik".
- Variant C, a box with no LVH Yard (a NAS, a Raspberry Pi): `lvh-yard register static <service> --host 192.168.1.50 --port 9000`, then `publish`.
- `lvh-yard test <service>` and `lvh-yard test <service> --workstation ws-b` as the check.

## Diagram idea

A decision diagram first: "What runs on the other box?" with three outcomes. Then one small flow diagram for each variant.

## Verification

Variants A and B: `mise run dev:up` starts two test Workstations on this computer. Variant C: use a listener on another address of this computer. Run it on a real LAN box if the maintainer has one, and label the page "tested by hand" for that part.

## Notes

The Yard needs the Gossip Key and the Invitation password to travel by different ways. Say so in the steps. Read the user guide section "Joining a Yard" before you write.

## Acceptance

- [x] The page starts in `draft_docs` of `zensical.toml`. It is `docs/guide/recipes/reach/another-computer.md`, the only page of "Reach other computers" for now.
- [x] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [x] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [x] Every command ran on the setup named under Verification, and the output on the page is real. See "Draft notes".
- [x] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [x] `mise run pages:docs-build` passes in strict mode, and the page renders in `pages:docs-serve`.
- [x] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

- Setup for variant A and B: two throwaway Workstations in `/tmp/rc08/wsa` and `/tmp/rc08/wsb`, made with `ws init --workstation ws-a|ws-b`, with other ports than the defaults, both with the Base Domain `lvh.me`, each with its own dashboard and Traefik. Both join over loopback, like `dev/workstations.sh`: `ws yard init --address 127.0.0.1` and `ws yard join ... --address 127.0.0.1`. The page uses the commands without `--address`, as on a real LAN. The setting `out-of-range` was set as in `dev/workstations.sh`, so loopback Instances are in range. No real second computer was used, so nothing was tested on a real LAN or through a real firewall.
- `ws tls ca create`, `ws yard init`, `ws compose recreate`, `ws yard share ws-b` (the page shows its `Zip password` lines, a throwaway password), `ws yard join`, `ws compose start` on ws-b, then `publish grafana --scope yard` on ws-b (a Container Binding to a Grafana container). `lvh-yard status` on both said `2 Workstations are in its WAN pool`. On ws-a, `catalog` listed `grafana.ws-b.lvh.me` as `healthy`, `curl` through the Traefik of ws-a answered `302`, and `lvh-yard test grafana --workstation ws-b` printed `Every check passed`.
- The output of `publish` on the page shows its first three lines only. The test printed a `risk: [Loopback Instances]` line too, because the Bound Instance of the test is on loopback, so the page leaves it out. The page does not show the output of `test`, because its addresses are loopback.
- Variant B: `ws compose stop`, `ws set traefik off`, `ws compose start` on ws-b. `status` on ws-b had no Traefik part, no process listened on the Traefik port of ws-b, and ws-a still answered `302` for `grafana.ws-b.lvh.me`. `lvh-yard remove traefik-admin` asks for `--id`, `--port` and the like, so the page uses `lvh-yard service delete traefik-admin`, which worked. `ws set traefik on` printed `compose recreate`; the page uses the stop, set and start sequence of the user guide for the way back.
- Variant C: a `python3 -m http.server` listener on `0.0.0.0:5055` reached at `192.168.1.67`. `register static nas --host 192.168.1.67 --port 5055` and `publish nas` gave `healthy`, `curl` answered `200`, and `test nas` printed `Every check passed`.
- Failures reproduced: `ws-b: Workstation unreachable` with `To see why, run: lvh-yard ws compose logs consul` appeared on ws-a in `catalog` about 25 seconds after `ws compose stop` on ws-b. The `status` of ws-a said `WARN Consul cannot read from ws-b`. `ws yard join` with the zip for ws-b on a Workstation named ws-c refused with `this Invitation is for ws-b, and this Workstation is ws-c. Ask the sender for an Invitation for ws-c`. A Static Instance at `127.0.0.1:4000` published with `--scope yard` printed `risk: [Loopback Instances] 127.0.0.1:4000 is on loopback. Other Workstations cannot reach it. Register it at the LAN address.` The page shows that a firewall causes `Workstation unreachable` as a possible cause only. The test did not block ports.
- Undo: `publish grafana --scope local` printed `Scope: Local`, `ws yard standalone` printed `This Workstation is standalone` and the `compose recreate` command.
- The dashboard has no form to join a Yard (no Invitation or Gossip text in the dashboard sources). The `Share with the Yard` switch is on the page of a Service.
- The sentence about Yard Scope on the page ("lets every Workstation of the Yard open it") comes from the glossary and the user guide. The warning before `ws yard share` comes from the user guide ("Keep the zip file and its password private", "Send the zip file and its password by two different ways").
- Not covered: a Windows Workstation joining a Yard. That is the recipe "Use a Windows laptop and a Linux server". The page does not show the Consul ports for Windows.

## Incident during the test

The first run used the folders `/tmp/rc08/ws-a` and `/tmp/rc08/ws-b`. Compose names a project after the folder, so the project names `ws-a` and `ws-b` matched the project names of the maintainer's `dev/ws-a` and `dev/ws-b`, and the containers mounted the existing Docker volumes `ws-a_consul-data` and `ws-b_consul-data` with the maintainer's test data. The test registered a Container Binding `grafana:3000` on the existing Service `grafana` of ws-b and published it with Yard Scope (its Scope was Yard before, so the Publication did not change). It removed the binding, ran `compose down` without `-v`, and removed `/tmp/rc08`. The volumes and the `dev/` folders were not deleted or changed (`.env` of both has its old date). The other data of the volumes is as the test found it, except Consul internals such as the Raft log. The second run used the folders `wsa` and `wsb`, with other project names and fresh volumes, and removed them with `compose down -v`. Rule for later tests: never name a test folder `ws-a` or `ws-b`.

## Review round

- The maintainer asked for a simpler diagram with one Service (`grafana`) throughout. The new diagram asks whether the other computer runs LVH Yard. With no LVH Yard, `grafana.lvh.me` comes from a Static Instance on this computer. With LVH Yard, the Service on ws-b is at `grafana.ws-b.lvh.me`, or at `grafana.lvh.me` through an Alias. A Static Instance, a Container Binding and a Compose binding give it Instances. The shared host with Traefik off is not in the diagram. It stays as a section of the steps.
- Alias test on two new throwaway Workstations (`wsa` and `wsb`): `lvh-yard alias add grafana --workstation ws-b --service grafana` printed `Created the Service grafana with Local Scope. Added the Alias to grafana on ws-b.` `lvh-yard alias list grafana` printed `grafana on ws-b: active`. Both `http://grafana.lvh.me` and `http://grafana.ws-b.lvh.me` through the Traefik of ws-a answered `302`. `alias remove` and `service delete grafana` removed them.
- Variant C now uses the Service `grafana` with a listener at `192.168.1.67:3030`, because hw_server holds port 3000 on this computer. `register static grafana --host 192.168.1.67 --port 3030` and `publish grafana` gave `healthy`, and `curl` answered `200`. The loopback risk output was captured again with `127.0.0.1:3030` on ws-b.
- Headings: `Steps` is now `CLI Steps`, `The same steps in the dashboard` is `Dashboard Steps`, and the symptoms sit in one `Troubleshooting` section with a `###` heading for each, on every page of the guide that has them: Install, Pick your Base Domain, Your first Service and the three recipes. The spec says so.
- The maintainer edited the page before this round: the shared host section is a `####` heading under the Yard variant, and the Undo section has the headings `Leave a Yard (Standalone)` and `Turn on Traefik`. I kept them.
- The test used the folders `wsa` and `wsb`, with their own volumes, and removed them. The volumes `ws-a_consul-data` and `ws-b_consul-data` are still there and untouched.

## Second review round

- The maintainer split the page in two. "Create a Yard and join it" (ticket 39) holds the Yard steps, their check, undo and two failures. This page keeps the publishing with Yard Scope, the Alias, the Static Instance for a computer with no LVH Yard, the shared host with Traefik off, and the `Workstation unreachable` and `risk: [Loopback Instances]` failures. It links to the Yard page for the firewall and the join.
- The CLI Steps of both pages have two columns (`ws-a` left, `ws-b` right). The columns are CSS: `docs/guide/stylesheets/columns.css`, loaded from `zensical.toml`, and the markup in `docs/development.md`. The steps stack below 60em with the Workstation name in front. Screenshots at 1400 px and at 700 px in the headless browser showed both layouts. The selector `:has()` needs a browser from 2023 or later.
- The Undo section of this page lost the Yard leave step, which moved to the Yard page. Its first heading is now `Stop sharing the Service`.

## Third review round: small screens

- The columns worked in the headless browser at 390, 600, 800 and 960 px (stacked) and at 961, 1000 and 1440 px (two columns), so the cause of the failure on the maintainer's screen is not known. The stacked steps had only the text `ws-a:` in front, and between 961 and 1150 px the columns were 300 px wide with cramped code.
- Changes: the columns start at 72em instead of 60em. The grid of a page with columns is wider (80rem) from 72em, so each column is 410 to 430 px at 1440 px. Each Workstation has a color, the colors of the diagrams (green and orange). A step has a colored bar on its left, and below 72em it also starts with a pill that holds the name of its Workstation. The stack is therefore readable on a phone. Checked at 390 px (with `isMobile`) and at 1440 px.
- If the failure is another one, such as a browser without `:has()` (before 2023), the steps show as one list without bars. Tell me the browser and the width.

## Fourth review round

- The maintainer moved the sections about a Workstation without Traefik (its commands and its way back) to the Install page, as an optional section. This page no longer mentions Traefik off. Its Undo has one section, `Stop sharing the Service`, which also covers a Static Instance.

## Fifth review round: page width

- The maintainer saw that both Reach pages used the full width, unlike the other pages. The cause was in `columns.css`: from 72em, `body:has(.lvhy-columns) .md-grid` set a wider grid (80rem) for the whole page, text and header included. It came from the small-screen redesign. The rule is gone, and the pages have the width of every other page. The columns are about 300 px wide at that width, with wrapped code.

## Sixth review round: the columns of this page did not show

- The editor of the maintainer reformatted `another-computer.md` with `proseWrap: always`. It joined the line `{ .ws-b }` to the text of its step, and `attr_list` does not read an attribute list in the middle of a paragraph, so the page lost its classes and its columns. The Yard page still had the old lines.
- Fix: a step now starts with an empty `<span class="ws-a"></span>` or `<span class="ws-b"></span>`, which no formatter changes, and `columns.css` selects the steps with `li:has(> p > span.ws-a)`. `.prettierrc.yaml` sets `proseWrap: preserve` for the guide too. Prettier with `--prose-wrap always` kept the span and the columns of the Yard page. `docs/development.md` describes the markup.

## Seventh review round: one column at every width

- The maintainer reads better with one column and a badge with the name and the color of the Workstation, also on a wide screen. The two-column layout is gone. `columns.css` became `steps.css` with the bar and the pill only, the block class is `lvhy-steps`, and the header row of the columns is removed from both pages. `docs/development.md`, the outline and ticket 39 say so. Every earlier note about columns in this ticket is history.

## Eighth review round: Markdown notation for the classes

- The maintainer asked for the Markdown notation `{ .ws-a }` instead of HTML. Both Reach pages now put the line `{ .ws-a }` or `{ .ws-b }` after the first paragraph of each step, and the `<div>` wrapper and the empty spans are gone, so the pages hold only Markdown. `steps.css` selects `li:has(> p.ws-a)` and `p.ws-a::before` inside `.md-typeset`, with no wrapper class. `docs/development.md` explains the line and why it must stay on its own line.
- The earlier loss of the columns came from an editor that wrapped the prose and joined this line to the text. `.prettierrc.yaml` sets `proseWrap: preserve` for the guide since then. A Prettier run with `--prose-wrap always` on the command line, which ignores the config file, still joins the line. Check the result in the browser if an editor setting overrides the config.
- Checked at 1440 px: 6 steps on the Yard page and 3 on the Reach page have the right bar color and pill.
