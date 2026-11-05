# 05: The Route Profiles page

**What to build:** The dashboard has a Route Profiles page where you see, add, change and remove profiles. See `.scratch/route-profiles-in-kv/spec.md`, user stories 1 to 8, 18, 24 and 25.

**Blocked by:** 02

**Status:** ready-for-agent

- [ ] The navigation bar has a Route Profiles link, highlighted on the page.
- [ ] The page lists each profile with its name, its Traefik type and the Services that use it.
- [ ] A form has a name field and a plain YAML text box in a monospace font.
- [ ] A "new profile" button fills the text box with one example: basic auth, IP allow list, headers, CORS or a chain.
- [ ] A refused save shows the reason and keeps the text in the form.
- [ ] You can change a profile from its row, and remove it. A refused remove shows the Services and chains that use it.
- [ ] The Service page links each named Route Profile to its row on the Route Profiles page.
- [ ] Page tests cover the list, the form, the examples, the kept text, remove, the navigation link and the Service page link.
- [ ] `docs/user-guide.md` explains the page.
