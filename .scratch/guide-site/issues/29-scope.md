# 29: Concepts: Scope

**What to build:** Local Scope and Yard Scope: who gets a hostname, and who can connect.

**Blocked by:** 01

**Status:** done

**Core for v0.2.0:** no

## The page covers

- A table of the two Scopes and the hostname that each gives.
- The warning that Scope decides who gets a hostname, not who can connect.
- The Out-of-range Instance rule.

## Verification

Check against ADR-0003 and the glossary.

## Acceptance

- [x] The page starts in `draft_docs` of `zensical.toml`.
- [x] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [x] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`.
- [x] Every command ran, and every output on the page is real.
- [x] `mise run pages:docs-build` passes in strict mode.
- [x] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

- The page is `docs/guide/concepts/scope.md`, titled "Scope". It is in `draft_docs`, and the group "Concepts" lists it after "How it works". The stage 1 text is in `drafts/29-scope.stage1.md`. `ste_check.py` reports no findings. It has the table of the two Scopes and their hostnames, a small diagram (`ws-a` green, `ws-b` orange), "Change the Scope", "What another Workstation sees", the warning "Scope decides who gets a hostname, not who can connect" and "Out-of-range Instances". It links to the Alias page, the Workstation CA page (one wildcard label), the Show a teammate page and SECURITY.md.
- Checked against ADR-0003 (the hostname of a Remote Service names its Home Workstation, so two coworkers can run `api`, and a wildcard covers one level) and the glossary entries Scope, Home Workstation, Remote Service and Out-of-range Instance.
- Tested on two throwaway Workstations in one Yard over loopback (folders `rcsca` and `rcscb`, names `ws-a` and `ws-b`, other ports than the defaults). `web` had a Static Instance at `127.0.0.1:4000`, and `lan` had one at the LAN address of this computer (`192.168.1.67`, a Python server on `0.0.0.0:4001`). The outputs on the page are real.
  - Local Scope: the catalog of `ws-b` printed `ws-a: no Services`, and `web.ws-a.lvh.me` and `web.lvh.me` answered `404` on `ws-b`. `catalog --scope every` listed the five Services of `ws-a` with Scope `local`, no hostname and no kind.
  - Yard Scope: `publish web --scope yard` printed `Other Workstations: web.ws-a.<base-domain>` and the risk `Loopback Instances`. The catalog of `ws-b` listed only `lan` (`lan.ws-a.lvh.me`, `[static]`), and `lan.ws-a.lvh.me` answered `200` through the Traefik of `ws-b`. `web.ws-a.lvh.me` answered `404`. `catalog --details --scope every` on `ws-b` showed the Instance `127.0.0.1:4000` as `passing ... out of range` with the risk line.
  - Port: with Local Scope, `http://192.168.1.67:4001` answered `200` and `http://192.168.1.67:4000` answered `000` (no connection). These requests came from this computer, to its LAN address, and not from another computer. `lan.ws-a.lvh.me` on `ws-b` answered `404` with Local Scope.
  - `publish lan` without `--scope` kept the Scope (`Local` after `--access-log on`, and `Yard` after `--scope yard`).
- The sentence "A Docker port that you publish without an address listens on every address" comes from the user guide and the test of the page "Show your work to a teammate". It was not tested again here. The 172.17.0.0/16 to 172.31.0.0/16 range comes from the glossary and the user guide.
- Not tested: a second computer, a firewall, and the switch `Share with the Yard` in the dashboard (the Service page of earlier tickets showed it).
- After the maintainer approves the page: remove it from `draft_docs`, mark the ticket done, delete the stage 1 draft, and commit.
