# 03: The Static Instances page and `lvh-yard register static`

**What to build:** The Register page becomes the Static Instances page, with a list and a form. The CLI gets `register static`. Adding a Static Instance can publish a new Service. See `.scratch/navigation-and-register/spec.md`, user stories 4 to 14 and 35.

**Blocked by:** 02

**Status:** ready-for-agent

- [ ] `POST /api/workstation/services/{service}/instances` takes an optional `publish` boolean.
- [ ] If the Service did not exist before the call, the call publishes it with Local Scope. A `publish` of `false` stops this.
- [ ] An existing Service keeps its Publication, or stays Unpublished.
- [ ] `GET /static-instances` lists this Workstation's Static Instances, with Service, address, Health and a link to the edit form.
- [ ] The page has a form for a Static Instance, with a "Publish a new Service" checkbox that is on by default.
- [ ] The form has no kind choice and no container fields.
- [ ] `GET /register` answers 301 to `/static-instances`.
- [ ] `lvh-yard register static <service>` takes the static options of today's `register`, plus `--no-publish`.
- [ ] Until ticket 05 is done, `lvh-yard register <service> --container ...` and its Compose options keep working.
- [ ] `openapi.yaml` documents the `publish` field.
- [ ] The user guide shows the Static Instances page and `register static`.
- [ ] `StaticInstancesTest`, the Static Instances page test (from `RegisterPageTest`) and the CLI test cover these points.
