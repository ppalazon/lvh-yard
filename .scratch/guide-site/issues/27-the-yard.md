# 27: Concepts: The Yard

**What to build:** What a Yard is, how Workstations find each other, and what Yard Scope gives.

**Blocked by:** 01

**Status:** done

**Core for v0.2.0:** no

## The page covers

- A diagram of two Workstations and the Peer join.
- The Gossip Key, the Invitation and the Main CA, in plain words.
- What happens when a Workstation is off (Unreachable Workstation).

## Verification

Check against the glossary and ADR-0001, ADR-0016 and ADR-0017.

## Acceptance

- [x] The page starts in `draft_docs` of `zensical.toml`.
- [x] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [x] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`.
- [x] Every command ran, and every output on the page is real.
- [x] `mise run pages:docs-build` passes in strict mode.
- [x] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Draft notes

- Page: `docs/guide/concepts/the-yard.md`, in `draft_docs`, nav "The Yard" in Concepts.
- Tested on two throwaway Workstations in one Yard (made with `ws tls ca create`, `ws yard init`, `ws yard share` and `ws yard join`):
  - `lvh-yard status` on the first one said `Consul has a leader, and 2 Workstations are in its WAN pool.`
  - After `compose stop` on the second one, the catalog printed `ws-b: Workstation unreachable` and the hint `lvh-yard ws compose logs consul`. `status` warned `Consul cannot read from ws-b, though its WAN pool lists it as alive.` The Services of the first one stayed healthy.
  - After `compose start`, the catalog listed `ws-b: no Services` again, so the Workstation answered.
  - `lvh-yard ws yard standalone` printed the lines that the page quotes (the Workstation Folder is cut to `...`).
- Not tested: what an Alias and a remote Route do while the Workstation is off. The page says that the Alias is `unreachable` and the Route uses the next source. That comes from the recipe "Swap a backend with an Alias" (ticket 18).
- `lvh-yard ws yard peers` listed the other Workstation on both sides (`ws-b 127.0.0.1:19312` on the first one, `ws-a 127.0.0.1:19302` on the joined one). `peers --sync` on the first one saved `YARD_PEERS=127.0.0.1:19312` in `.env` and printed the `compose recreate` command. An earlier run printed only a header on the joined Workstation because I ran the command with the wrong `--dashboard` and `--dir` flags.
- The warning about the Gossip Key follows SECURITY.md.
- The statements about who can share and about `standalone` come from ADR-0017.
- Review round: the maintainer asked for an activity diagram with the steps and a final peers sync step. The diagram now shows three lanes: `ws-a` shares, `ws-b` joins, `ws-a` runs `ws yard peers --sync`. The maintainer also asked whether the Main CA could be called Yard CA. The glossary says to avoid "Yard CA" (ADR-0016 replaced it), and the CLI says "main CA", so the page keeps Main CA.
- Second review round: the maintainer replaced the step diagram with a structure diagram. It shows two Workstations, each with a Consul box. The Consul boxes talk with TLS (certificates of the Main CA) and with gossip (encrypted with the Gossip Key). `ws-a` holds the Main CA, which signs its own Consul certificate and the one of `ws-b`, carried in the Invitation. The steps of a join stay in the recipe "Create a Yard and join it". The `ws yard peers --sync` step is now only in the Peer bullet.
