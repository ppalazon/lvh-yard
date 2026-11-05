# 02: One Catalog row for each Service

**What to build:** The Catalog shows one row for each Service, with the columns Service, State, Hostname and Instances. The Instances cell gives a count. See `.scratch/catalog-per-service/spec.md`, user stories 10 to 14.

**Blocked by:** 01

**Status:** done

- [x] Each Service has one row, whatever its number of Instances.
- [x] The Instances cell says "N of M passing", or "No Instances".
- [x] An Unpublished Service shows its count too.
- [x] The Instance and Health columns are gone.
- [x] Filters, the 5-second refresh and the Workstation headings work as before.
- [x] `docs/user-guide.md` describes the new Catalog columns.
- [x] `CatalogPageTest` covers one row for a Service with several Instances, the count text and "No Instances".

## Comments

Implemented in `b2dc430`, merged in `38440ab`. The integration check passed. Browser smoke checks showed the four columns, one row for two Instances, passing counts, No Instances, an Unpublished count and the live Degraded filter. Standards and Spec reviews found no Catalog page issues.
