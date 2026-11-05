# 02: Links that leave the dashboard open in a new tab

**What to build:** A Service hostname or an Alias hostname opens in a new tab, with a small icon that says so. Links between dashboard pages stay in the same tab. See `.scratch/dashboard-ux/spec.md`, user stories 27 to 31.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] One shared view helper makes a link that leaves the dashboard. It sets `target="_blank"` and `rel="noopener noreferrer"`.
- [ ] The helper adds a small Lucide external-link icon that screen readers do not read.
- [ ] Service hostnames, on the Catalog and on the Service page, use the helper.
- [ ] Alias hostnames on the Aliases page use the helper.
- [ ] Any other link to an `http://` hostname uses the helper.
- [ ] Links to dashboard pages have no `target` and no `rel`.
- [ ] Page tests cover a Service hostname, an Alias hostname and a link to a dashboard page.
