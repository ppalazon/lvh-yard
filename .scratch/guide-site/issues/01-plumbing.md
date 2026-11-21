# 01: Plumbing: move the landing page, add the guide build

**What to build:** Move the landing page to `docs/landing/` and the install scripts to `dev/`. Add `zensical.toml`, an empty guide in `docs/guide/`, the five `pages:*` mise tasks, the pinned Zensical, and the new `pages.yml`. No guide prose yet.

**Blocked by:** none

**Status:** done

**Core for v0.2.0:** yes

## Acceptance

- [x] The files move: `docs/site/{index.html,site.css,assets}` to `docs/landing/`, and `docs/site/install.sh` and `docs/site/install.ps1` to `dev/`. `docs/site/` is gone. The moved files are byte-identical to the old ones, except the comment on line 2 of `site.css`.
- [x] `zensical.toml` at the root sets `docs_dir = "docs/guide"` and `site_dir = "dist/pages/docs"`. It keeps the explicit `nav`, with the home page only, and an empty list of other pages. The home page, `docs/guide/index.md`, is a placeholder in `draft_docs`, so the first deploy publishes no page. The default Zensical palette stays: the theme does not follow the dashboard colors yet. A later ticket can add `extra_css` if the maintainer wants it.
- [x] Mermaid renders in the build: a test page produced `<pre class="mermaid">` in the HTML. The test page is removed.
- [x] `mise.toml` has `pages:landing-build`, `pages:docs-build`, `pages:build`, `pages:landing-serve` and `pages:docs-serve`. The old `site:build` and `site:serve` are gone. `pages:landing-build` removes only its own files and keeps `dist/pages/docs/`.
- [x] `mise.toml` pins `uv = "0.13.9"` and `"pipx:zensical" = "0.1.3"`. `mise run pages:build` works from an empty `dist/`.
- [x] `/dist/` is in `.gitignore`. `/docs/site/dist/` is gone.
- [x] `.github/workflows/pages.yml` runs `pages:build`, uploads `dist/pages`, and watches `docs/landing/**`, `docs/guide/**`, `zensical.toml`, `dev/install.*`, the dashboard `theme.js` and `theme.css`, `package-lock.json` and itself. A pull request runs the same build and deploys nothing. `actionlint` passes.
- [x] These references are updated: `README.md`, `docs/development.md`, `dev/test-windows-native.ps1`, `cli/internal/update/update.go`, `CODING_STANDARDS.md`, `docs/landing/site.css`, `dashboard/src/main/css/theme.css` and `.github/workflows/ci.yml` (the Windows installer path).
- [x] `docs/development.md` says how to build and preview the landing page and the guide.
- [x] Proof: `rm -rf dist && mise run pages:build` ran with no error. `dist/pages/index.html`, `install.sh`, `install.ps1` and the `docs/` folder exist, and the install scripts match the files of `dev/`. `pages:landing-serve` answers 200 for `/`, `/docs/` and `/install.sh`. `pages:docs-serve` answers on port 8000.

## Facts learned

- A page in `draft_docs` can stay in the explicit `nav`. The strict build passes and leaves the page out.
- A link from a published page to a page in `draft_docs` fails the strict build. This protects the rule that only live pages are linked.
- A build with only draft pages passes and writes no `index.html`.
- The pipx backend of mise installs Zensical through uv, so `zensical` is on the PATH of every task and of CI.

See `../spec.md` for the decisions, the writing rules and the recipe template.
