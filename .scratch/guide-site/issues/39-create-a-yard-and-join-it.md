# 39: Recipe: Create a Yard and join it

**What to build:** Write the recipe "Create a Yard and join it" with the recipe template of the spec. The maintainer split it from ticket 08 ("Reach a service on another computer") in the review of that page.

**Blocked by:** 01

**Status:** done

**Core for v0.2.0:** yes

## Pain

"Each of my computers sees only its own Services."

## Result

`ws-b` joins the Yard of `ws-a`. Then each Workstation lists the Services of the other.

## Commands to use

- On `ws-a`: `lvh-yard ws tls ca create`, `lvh-yard ws yard init`, `lvh-yard ws compose recreate`, `lvh-yard ws yard share ws-b`.
- On `ws-b`: `lvh-yard ws yard join ws-b-yard.zip`, `lvh-yard ws compose recreate`.
- Check with `lvh-yard status` on both. Undo with `lvh-yard ws yard standalone`.

## Diagram

A sequence diagram with the boxes `ws-a` (green) and `ws-b` (orange).

## Layout

The CLI Steps are one list. Each step has a colored bar and a pill with the name of its Workstation (`ws-a` green, `ws-b` orange). See `docs/development.md` and `docs/guide/stylesheets/steps.css`.

## Acceptance

- [x] The page starts in `draft_docs` of `zensical.toml`. It is `docs/guide/recipes/reach/create-a-yard.md`, the first page of "Reach other computers".
- [x] The maintainer approved the page, and it left `draft_docs`.

## Draft notes

- The commands and the outputs come from the tests of ticket 08: two throwaway Workstations (`wsa` and `wsb`, with their own volumes) that join over loopback. `ws yard init` ran with `--address 127.0.0.1`, and the page shows the command without it, as on a real LAN. The `status` output shows its first two lines. The failure `Consul cannot read from ws-b` came from `ws compose stop` on `ws-b`, and the refusal of the invitation from a Workstation named `ws-c`. Nothing was tested on a real LAN, through a real firewall or on Windows.
- The text of the warning before `ws yard share` comes from the user guide.
