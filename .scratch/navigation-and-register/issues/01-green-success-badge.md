# 01: Good states get a green success badge

**What to build:** A new `success` badge variant, green in both themes, for every good state. See `.scratch/navigation-and-register/spec.md`, user stories 33 and 34.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `app.css` has a badge variant `success` with `bg-green-600 text-white`, in the light and the dark theme.
- [ ] Passing Health uses the `success` variant on the Catalog, Service and Containers pages.
- [ ] The Healthy Service state uses the `success` variant.
- [ ] An OK Status Part uses the `success` variant on the status page.
- [ ] A good Reachability result uses the `success` variant.
- [ ] Warning, critical and the other badges keep their variants.
- [ ] The page tests cover the variant of each good state.
