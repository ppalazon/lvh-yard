# 02: One Catalog row for each Service

**What to build:** The Catalog shows one row for each Service, with the columns Service, State, Hostname and Instances. The Instances cell gives a count. See `.scratch/catalog-per-service/spec.md`, user stories 10 to 14.

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] Each Service has one row, whatever its number of Instances.
- [ ] The Instances cell says "N of M passing", or "No Instances".
- [ ] An Unpublished Service shows its count too.
- [ ] The Instance and Health columns are gone.
- [ ] Filters, the 5-second refresh and the Workstation headings work as before.
- [ ] `docs/user-guide.md` describes the new Catalog columns.
- [ ] `CatalogPageTest` covers one row for a Service with several Instances, the count text and "No Instances".
