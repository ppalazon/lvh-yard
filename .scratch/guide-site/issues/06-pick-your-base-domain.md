# 06: Start: Pick your Base Domain

**What to build:** The page that teaches the first principle: for development, the Base Domain is a wildcard DNS that answers `127.0.0.1`. It follows the spec section "The Base Domain".

**Blocked by:** 01

**Status:** ready-for-human

**Core for v0.2.0:** yes

## Reader's need

The reader meets the question "Base Domain:" in `ws init` and does not know what to type.

## The page covers

- The diagram of the spec: browser, DNS, `127.0.0.1`, local Traefik, Instance, and the Yard Scope hostname `grafana.ws-b.<Base Domain>`.
- The four options in the order of the spec, with `lvh.me` and `l.lvhy.cc` as the examples. `l.lvhy.cc` is this project's domain.
- The approved warning about a shared domain, as an admonition.
- How to test a choice before you use it: a command that shows the answer for `anything.<Base Domain>` and the other levels (`a.ws-b.<Base Domain>`).
- How to change it later: `lvh-yard ws set base-domain <domain>` then `lvh-yard ws compose recreate`.
- A pointer to recipe "Your DNS blocks the Base Domain".

## Verification

Resolve `a.l.lvhy.cc`, `x.ws-b.l.lvhy.cc` and `a.lvh.me` again on the day you write. Test `localhost` (see the open check in the spec) on Linux, and say what you did not test on macOS and Windows. Test the change of Base Domain on a test Workstation. This is the first page to draft. It sets the voice.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The text passes the `ste80-humanizer` Humanize pass and uses only the terms of `GLOSSARY.md`.
- [ ] Every command ran, and every output on the page is real.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
