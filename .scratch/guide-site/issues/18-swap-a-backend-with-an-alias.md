# 18: Recipe 10: Swap a backend with an Alias

**What to build:** Write the recipe "Recipe 10: Swap a backend with an Alias" with the recipe template of the spec.

**Blocked by:** 01, 17

**Status:** done

**Core for v0.2.0:** no

## Pain

"I want my local `web` to use the `api` that runs on a coworker's Workstation, and go back later."

## Result

An Alias adds the Instances of a Remote Service to your Service. You remove it to go back.

## Commands to use

- `lvh-yard alias add api --workstation ws-b --service api`, `lvh-yard alias list api`, `lvh-yard alias remove api --workstation ws-b --service api`.
- `lvh-yard priority api` to order the Alias against a Static Instance.

## Diagram idea

A flow diagram: the Route of `api` on ws-a uses the Instances of `api` on ws-b.

## Verification

`mise run dev:up`. Read the glossary entries for Alias and Remote Service first, and the Out-of-range rule: an Alias to a loopback Instance does not route.

## Acceptance

- [x] The page starts in `draft_docs` of `zensical.toml`.
- [x] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [x] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [x] Every command ran on the setup named under Verification, and the output on the page is real. A part that nobody tested says so on the page.
- [x] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [x] `mise run pages:docs-build` passes in strict mode.
- [x] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

- The page is `docs/guide/recipes/choose/swap-with-an-alias.md`, titled "Swap a backend with an Alias" as in the outline. It is in `draft_docs`, and the navigation lists it in "Choose which Instance answers". The stage 1 text is in `drafts/18-swap-with-an-alias.stage1.md`. `ste_check.py` reports only the allowed `Troubleshooting` heading. It uses the badges `ws-a` (green) and `ws-b` (orange) for the steps of each Workstation. The diagram shows the Route of `api` on `ws-a` with the Alias first and the Static Instance as the fallback.
- Tested on two throwaway Workstations in one Yard over loopback (folders `rcala` and `rcalb`, names `ws-a` and `ws-b`, other ports than the defaults), as in the tickets of the Reach pages. `ws-b` had `api` at the LAN address of this computer (`192.168.1.67`, a Python server on `0.0.0.0:4002`) with Yard Scope, and `ws-a` had `api` at `127.0.0.1:4001`. The outputs on the page are real.
  - `catalog` on `ws-a` listed the `api` of `ws-b` with `yard` Scope. `alias add api --workstation ws-b --service api` printed `Added the Alias to api on ws-b to the Service api.`, `alias list api` printed `api on ws-b: active`, and `priority api` put the Alias second. Adding it again printed `Service api has the Alias to api on ws-b already.` `priority api ws-b/api` put it first, and the hostname then answered from `ws-b`. The catalog said `[alias]`, and `test api` printed `Instance 192.168.1.67:4002 (in the Route)` and `The Route sends requests to the Alias's Remote Service: api on ws-b.`
  - Publishing `api` with Yard Scope while the Alias was first printed `risk: [Alias order] The Alias to ws-b/api is above another Instance Source. Other Workstations skip Aliases. Move it below the other sources.`, as the user guide says.
  - `alias remove` printed `Removed the Alias to api on ws-b from api.`, `alias list` printed `api has no Aliases.`, and the hostname answered from `ws-a` again.
  - Refusals: an Alias to a Service of `ws-b` with Local Scope printed `ws-b publishes notes with Local Scope only. Only a Remote Service with Yard Scope can be the target of an Alias.` with exit 1, and an Alias to `ws-a` printed `An Alias is only for a Remote Service, and ws-a is this Workstation.` with exit 1.
  - States: `ws-b` set `api` to Local Scope, and `alias list` printed `not-shared` with `ws-b publishes api with Local Scope only.` An Alias to a Service of `ws-b` with an Instance on loopback printed `Warning: api2 has no Instance this Workstation can reach.` and the state `unavailable`. With `ws-b` stopped (`ws compose stop`), after about 40 seconds `alias list` printed `unreachable` with `Workstation ws-b is unreachable: its Consul does not answer.`, and the hostname answered from the Static Instance of `ws-a`. Starting `ws-b` made the Alias `active` again.
  - Dashboard: the page of a Remote Service has the button `Alias` and opens the form. The page of your own Service has `Add Alias` in the `Instance Sources` card. The form has the selector `Remote Service` (it lists `api on ws-b`) and `Add`. `Add` returned to the Service page, and the Alias was last. The icon button `Move Alias to api on ws-b up` put it first, and the hostname answered from `ws-b` (checked with `priority` and `curl`). The form that creates a new Service, and the Alias button of the Remote Service page, were not submitted.
- Not tested: a second computer (the Yard is on one computer, over loopback), and several Aliases in one Service (the order of `ws-b` and `ws-c`).
- After the maintainer approves the page: remove it from `draft_docs`, mark the ticket done, delete the stage 1 draft, and commit.
