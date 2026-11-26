# 36: Release gate: publish the core of the site for v0.2.0

**What to build:** Check that the core pages and the landing page are approved, then publish.

**Blocked by:** 01, 02, 03, 04, 05, 06, 07, 08, 09, 11, 13, 14, 19, 20, 31, 37

**Status:** ready-for-human

**Core for v0.2.0:** yes

## Acceptance

- [ ] Every ticket in `Blocked by` is `done`. The maintainer approved each page.
- [ ] `zensical.toml` lists no approved page in `draft_docs`. Every unapproved page is still listed.
- [ ] `mise run pages:build` passes, and `pages.yml` ran on `main`. `https://lvhy.cc/` and `https://lvhy.cc/docs/` answer 200, and `https://lvhy.cc/install.sh` and `https://lvhy.cc/install.ps1` serve the files of `dev/`.
- [ ] Every link on the landing page and in the guide works. Every landing card links to a live page.
- [ ] The README links to the guide at `https://lvhy.cc/docs/`.
- [ ] `docs/development.md` says how a release publishes the site.
- [ ] The maintainer tags `v0.2.0`. The tag `v0.2.0-rc.1` exists already.
- [ ] Delete `.scratch/guide-site/` when every ticket is `done`, and write a trail learning only for a trap or a choice that the code does not show.

See `../spec.md` for the decisions, the writing rules and the recipe template.

## Gate check on this branch (`initial-docs`)

- Every ticket in `Blocked by` is `done`. `draft_docs` in `zensical.toml` is empty, and the guide has no unapproved page.
- `mise run pages:build` passes: the strict build of the guide reports "No issues found". A check of every `href` and `src` of every page in `dist/pages` found no broken internal link. The 32 external links answer 200, except the links that need a running Workstation (`http://yard.lvh.me`, `http://consul-ui.lvh.me`, `http://traefik-admin.lvh.me` and `http://127.0.0.1:7780`) and `https://fonts.gstatic.com` (a preconnect, not a page). The self-links `https://lvhy.cc/docs/...` were 404 before the guide was deployed.
- `https://lvhy.cc/`, `https://lvhy.cc/docs/`, `https://lvhy.cc/install.sh` and `https://lvhy.cc/install.ps1` answer 200 (observed 2026-11-26, after the branch merged and `pages.yml` ran on `main`). The self-links of the previous item work now.
- The README had no link to the guide. It has one now, `[Guide](https://lvhy.cc/docs/)`, as the first item of "Documentation", and the User guide line says that it is the full reference.
- `docs/development.md` says how the site publishes: `pages.yml` builds and deploys when a file of the landing page, the guide or the install scripts changes on `main`, and it lists the one-time Pages settings.

### Open items for the maintainer

- Done: `initial-docs` is merged and `pages.yml` ran. The Pages settings (Source: GitHub Actions, custom domain `lvhy.cc`) are set, because the site answers 200.
- Tag `v0.2.0`. The install default "latest release" works only after the first stable release: `releases/latest` answers 404 while only `v0.2.0-rc.1` exists.
- Not tested: the Windows commands of the guide, a real LAN, a real firewall and a real public CA. The browser-trust commands for Linux and macOS are not in the HTTPS recipe. Ticket 38 (`ws init` suggests `localhost`) is `needs-triage`.
- Delete `.scratch/guide-site/` only when every ticket is `done` or dropped. The optional tickets (10, 12, 15 to 18, 21 to 30, 32 to 35) are open.
