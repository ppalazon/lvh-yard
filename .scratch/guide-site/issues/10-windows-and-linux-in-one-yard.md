# 10: Recipe 17: A Windows laptop and a Linux server in one Yard

**What to build:** Write the recipe "Recipe 17: A Windows laptop and a Linux server in one Yard" with the recipe template of the spec.

**Blocked by:** 01, 08

**Status:** ready-for-human

**Core for v0.2.0:** no

## Pain

"My laptop runs Windows and my HomeLab box runs Linux."

## Result

Both join one Yard, and each opens the Services of the other.

## Commands to use

- The user guide sections "Windows Workstation setup" and "Joining a Yard from Windows".
- `lvh-yard ws yard share` and `join`, with the Windows limits: IPv4 only, host networking in Docker Desktop, an executable that is not signed.

## Diagram idea

A diagram of the two Workstations and the Consul federation ports between them.

## Verification

Needs real Windows 11 and Docker Desktop. Use `docs/windows-test-vm.md`. Label the page "tested by hand".

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page has the eight parts of the recipe template, in order, with one Mermaid diagram.
- [ ] Every command ran on the setup named under Verification, and the output on the page is real. A part that nobody tested says so on the page.
- [ ] The text passes the `ste80-humanizer` Humanize pass and uses only the terms of `GLOSSARY.md`. A warning comes before the command that it protects.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
