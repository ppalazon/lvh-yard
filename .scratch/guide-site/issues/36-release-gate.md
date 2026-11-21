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
