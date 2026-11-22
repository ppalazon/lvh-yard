# 02: Landing page: pains, Base Domain, questions, links

**What to build:** Change `docs/landing/index.html` as the spec section "The landing page" says. Add the Docs link, the section "Problems it solves", the Instance Priority SVG, the Base Domain block, the eight questions, the dashboard-login text, and the new `/docs/` links. Tighten the copy.

**Blocked by:** 01

**Status:** ready-for-human

**Core for v0.2.0:** yes

## Acceptance

- [ ] The header has a Docs link to `/docs/`. Both "Read the user guide" links point to `/docs/`.
- [ ] "Problems it solves" has six cards, one for each pain of the spec. Each card has the pain, one command, and a link. A link to a page that is not live is absent, not broken.
- [ ] The Instance Priority SVG follows the style of the Yard diagram: colors only from the theme variables, no script, and an accessible title and description.
- [ ] "Pick your Base Domain" shows the loopback wildcard idea, names `lvh.me` and `l.lvhy.cc` as shared domains, says that `l.lvhy.cc` is this project's domain, and recommends an own domain. It carries the approved warning in short form.
- [ ] The block of eight typical questions follows the spec. Each answer has two sentences at most. The answer to question 5 says that `lvh.me` can stop working.
- [ ] "Use it on a network that you trust" carries the approved dashboard-login text.
- [ ] The copy was written with `technical-writing` and polished with `ste80-humanizer`, as the spec describes, and `ste_check.py` reports no findings. The hero keeps its meaning.
- [ ] Proof: `mise run pages:landing-serve`, then a browser check in light and dark themes, and at a narrow width. The page works with JavaScript off, except the hero animation, which has its text in `sr-only`.
- [ ] The maintainer approved the page.

See `../spec.md` for the decisions, the writing rules and the recipe template.
