# 05: Start: Install

**What to build:** Install the CLI, run `ws init`, start the Workstation, and check it. One page for Linux and macOS, with PowerShell tabs for Windows 11.

**Blocked by:** 01

**Status:** ready-for-human

**Core for v0.2.0:** yes

## Reader's need

The reader wants a working Workstation in five minutes, without reading the whole user guide.

## The page covers

- `curl -fsSL https://lvhy.cc/install.sh | sh`, and the PowerShell command from the README, as tabs.
- `lvh-yard ws init`, with a pointer to Pick your Base Domain for its second question.
- `docker compose up -d` in the Workstation Folder, and `lvh-yard status`.
- A diagram of what `ws init` writes and what Docker starts (Consul, the dashboard, Traefik).
- Requirements: Docker with Compose, and the Windows limits. Docker is the only runtime. Podman is not supported.
- The dashboard-has-no-login warning, before the first command that starts it.

## Verification

Run the steps on a clean Linux user. Windows: label "tested by hand" and use `docs/windows-test-vm.md`.

## Acceptance

- [ ] The page starts in `draft_docs` of `zensical.toml`.
- [ ] The maintainer and the writer draft it together. The writer does not generate the page alone, and does not move to the next page before the maintainer approves this one.
- [ ] The page was drafted with `technical-writing` and polished with `ste80-humanizer`, as the spec describes. `ste_check.py` reports no findings, and the text uses only the terms of `GLOSSARY.md`.
- [ ] Every command ran, and every output on the page is real.
- [ ] `mise run pages:docs-build` passes in strict mode.
- [ ] The maintainer approved the page, and it left `draft_docs`.

See `../spec.md` for the decisions, the writing rules and the recipe template.
