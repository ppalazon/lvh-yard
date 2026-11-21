# 01: Plumbing: move the landing page, add the guide build

**What to build:** Move the landing page to `docs/landing/` and the install scripts to `dev/`. Add `zensical.toml`, an empty guide in `docs/guide/`, the five `pages:*` mise tasks, the pinned Zensical, and the new `pages.yml`. No guide prose yet.

**Blocked by:** none

**Status:** ready-for-human

**Core for v0.2.0:** yes

## Acceptance

- [ ] `git mv` moves `docs/site/{index.html,site.css,assets}` to `docs/landing/`, and `docs/site/install.sh` and `docs/site/install.ps1` to `dev/`. `docs/site/` is gone.
- [ ] `zensical.toml` at the root sets `docs_dir = "docs/guide"` and `site_dir = "dist/pages/docs"`, lists the site name and the navigation tree of the spec, and lists every unfinished page in `draft_docs`. The theme follows the dashboard colors if Zensical allows it.
- [ ] `docs/guide/index.md` exists, so the strict build has a home page. Mermaid diagrams render in the build.
- [ ] `mise.toml` has `pages:landing-build`, `pages:docs-build`, `pages:build`, `pages:landing-serve` and `pages:docs-serve`, as the spec describes. The old `site:build` and `site:serve` tasks are removed. `pages:landing-build` does not delete `dist/pages/docs/`.
- [ ] `mise.toml` pins Zensical through `uv`. `mise run pages:build` works on a clean checkout.
- [ ] `/dist/` is in `.gitignore`. The line `/docs/site/dist/` is gone.
- [ ] `.github/workflows/pages.yml` runs `pages:build`, uploads `dist/pages` and watches the paths of the spec. A pull request runs `pages:docs-build` too, so a broken link fails it.
- [ ] Every file that the spec lists as naming `docs/site/` is updated: `README.md`, `docs/development.md`, `dev/test-windows-native.ps1`, `cli/internal/update/update.go`, `CODING_STANDARDS.md`, `docs/landing/site.css`, `dashboard/src/main/css/theme.css`, and the Windows install tests in `docs/development.md` that name `docs/site/install.ps1`.
- [ ] `docs/development.md` says how to build and preview the landing page and the guide.
- [ ] Proof: `mise run pages:build` prints no error. `dist/pages/index.html`, `dist/pages/install.sh`, `dist/pages/install.ps1` and `dist/pages/docs/index.html` exist. `cmp dev/install.sh dist/pages/install.sh` matches. `mise run pages:landing-serve` shows the same page as before the move.

See `../spec.md` for the decisions, the writing rules and the recipe template.
