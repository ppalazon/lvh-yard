# 34: Reference: Troubleshooting

**What to build:** A table of symptoms, causes and commands. It links to recipe 14 and to the Base Domain recipe.

**Blocked by:** 01, 24

**Status:** ready-for-human

**Core for v0.2.0:** no

## The page covers

- Symptoms: a name does not resolve, a Route answers 404 or 502, a Service is Unavailable, Unhealthy or Degraded, a Remote Service is missing, an Instance is marked Out-of-range, a Workstation is unreachable, a certificate is not trusted.
- For each: the one command that shows the cause, and the fix.

## Verification

- Reproduce each symptom on `dev:up`.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`.
- [ ] Every command ran, and every output on the page is real.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
