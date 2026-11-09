# 01: Create and delete Services

**What to build:** You create an empty Service from the catalog with only a name, and you delete it from its page. The CLI does the same. See `.scratch/service-workflow/spec.md`, user stories 1 to 13, 64 to 66 and 73.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [x] A Service record in this Workstation's Consul KV makes a Service exist, with no Instances and no Publication.
- [x] A Service that only Foreign Instances give still shows, without a record.
- [x] The REST API creates a Service by name. It refuses a name that is not one DNS label, and a name that this Workstation already uses.
- [x] The REST API deletes a Service. A delete removes its Publication, Static Instances, Container Bindings and their Bound Instances. Containers keep running.
- [x] After a delete, a Service with Foreign Instances left stays, and its page says why.
- [x] Publishing, a new Static Instance and a new Container Binding for a missing Service answer 404.
- [x] The catalog has a "New Service" button. The form asks only for a name and opens the new Service's page.
- [x] The catalog shows empty Services and marks them as empty.
- [x] The Service page has "Delete Service" at the bottom, with a confirmation that names what goes.
- [x] `lvh-yard service create <name>` and `lvh-yard service delete <name>` work. `lvh-yard catalog` shows empty Services.
- [x] `register static`, `register container` and `register compose` attach to an existing Service. On a missing Service, they fail and name `lvh-yard service create`. `--no-publish` goes away.
- [x] Component tests cover create, delete, each refusal, the 404 answers, and the catalog and page parts.
- [x] CLI tests cover the new commands and `register` on a missing Service. The OpenAPI test covers the endpoints.
- [x] `docs/user-guide.md` explains how to create and delete a Service.
