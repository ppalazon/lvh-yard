# 02: A navigation bar in the header of every page

**What to build:** The header of every page has links to Catalog, Containers, Aliases and Static Instances, and the Catalog page loses its three buttons. See `.scratch/navigation-and-register/spec.md`, user stories 1 to 3.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] The header has four links: Catalog (`/`), Containers (`/containers`), Aliases (`/aliases`) and Static Instances (`/static-instances`).
- [ ] The link of the current page has `aria-current="page"` and looks highlighted.
- [ ] The Service page, the edit page and the status page have no current link.
- [ ] The Catalog page has no Containers, Aliases or Register buttons.
- [ ] Until ticket 03 is done, `/static-instances` answers 301 to `/register`.
- [ ] The header fits at phone width.
- [ ] The page tests cover the links and the current link on each page.
