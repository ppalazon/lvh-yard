# 09: Recipe 11: Show your work on the LAN

**What to build:** Write the recipe "Recipe 11: Show your work on the LAN" with the recipe template of the spec.

**Blocked by:** 01, 06

**Status:** done

**Core for v0.2.0:** yes

## Pain

"My teammate cannot open my service."

## Result

A teammate with a Workstation opens it at `<service>.<workstation>.<Base Domain>`.

## Commands to use

- `lvh-yard publish <service> --scope yard`.
- `lvh-yard test <service> --workstation ws-a` as the check.

## Diagram idea

One diagram: Yard Scope across two Workstations.

## Verification

`mise run dev:up`. Repeat the dashboard-has-no-login warning: Scope decides who gets a hostname, not who can connect.

## Open question

A device that cannot run a Workstation, such as a phone, cannot use LVH Yard on its own, because a Workstation needs Docker with Compose. The recipe makes no promise for it. The maintainer wants to think about it later.

## Acceptance

- [x] The page starts in `draft_docs` of `zensical.toml`. It is `docs/guide/recipes/reach/show-your-work.md`, the third page of "Reach other computers".
- [x] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [x] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [x] Every command ran on the setup named under Verification, and the output on the page is real. See "Draft notes".
- [x] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [x] `mise run pages:docs-build` passes in strict mode.
- [x] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

- Setup: two throwaway Workstations (folders `r09a` and `r09b`, so their Compose projects have their own volumes; Workstation names `ws-a` and `ws-b`) in one Yard over loopback, as in ticket 08: `ws yard init --address 127.0.0.1`, `share`, `join`. `ws-a` is the author, `ws-b` the teammate. No second computer and no real LAN. The folders, the volumes and the listener are removed. The volumes `ws-a_consul-data` and `ws-b_consul-data` of the maintainer's dev folders are still there.
- The simulation could not use the LAN address as the address of a Workstation, so the Instance was registered with `--host 192.168.1.67`, the LAN address of this computer. In a Yard, `register static` without `--host` gives the address of the Workstation (its LAN address), as the CLI tests of the ticket-07 correction show. The page uses the command without `--host`. A test of `register static web --port 4000` in this setup would give `127.0.0.1`.
- Tested: `publish web --scope yard` printed `Scope: Yard` and `Other Workstations: web.ws-a.<base-domain>`. On `ws-b`, `catalog` listed `web` under `ws-a` as `web.ws-a.lvh.me`. With the app on `0.0.0.0:4000`, `lvh-yard test web --workstation ws-a` on `ws-b` printed `ok` for the Instance and for the Route (`answered 200`) and `Every check passed`, and `curl http://web.ws-a.lvh.me` through the Traefik of `ws-b` answered `200`. The `Check` output on the page shows its first three lines.
- Failures, with the exact outputs: the app bound to `127.0.0.1` gave `Connection refused` on the Instance at `192.168.1.67:4000` and `Traefik answered 502`, and `curl` answered `502`. `http://web.lvh.me` on `ws-b` answered `404 page not found`, while `web.ws-a.lvh.me` answered `200`. `Workstation unreachable` points to the Yard page, where the cause was reproduced in ticket 08.
- `lvh-yard publish web --scope local` and `service delete` come from the ticket-08 tests of the same commands.
- The warning before `publish` comes from the glossary ("It decides who gets a hostname, not who can connect") and the user guide ("Local Scope ... does not make a Host Port private ... A port that Docker publishes on every address stays open on the LAN. A process that listens only on loopback does not accept connections at the LAN address").
- The open question of the ticket stays open: the page makes no promise for a phone or any device that cannot run a Workstation.

## Review round: the classes and the formatter

- The editor of the maintainer (nvim) formats the guide with a prose wrap. It joined the line `{ .ws-a }` to the text of its step on all three Reach pages, and it split `{ .ws-a }` across two lines on one step, so `attr_list` lost the classes. The `proseWrap: preserve` of `.prettierrc.yaml` does not stop it, so the editor passes the option itself.
- Fix: the class now follows an inline element. Each step, bullet or paragraph of a Workstation starts with `**ws-a**{.ws-a}` or `**ws-b**{.ws-b}`, with no space before `{` and none inside the braces, so no wrap can separate the class from its text. `steps.css` styles `strong.ws-a` and `strong.ws-b` as a pill and gives a list item with the badge a bar. The text "On `ws-a`," and "On this computer," in the three Reach pages is the badge now. In the dashboard steps, the badge is only on the bullet where the Workstation changes.
- Checked: `prettier --prose-wrap always` keeps all the classes of the three pages (9, 7 and 8 badges). The browser showed 8, 7 and 9 badges, bars on the list items, and no literal `{.ws-` text. The maintainer's own `{.ws-a}` on the Check paragraph named `ws-a`, and the step runs on `ws-b`, so the badge says `ws-b`.
- The same change is in the pages of tickets 08 and 39. `docs/development.md` describes the badge.
