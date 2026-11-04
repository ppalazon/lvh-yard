# 03: The Static Instances page and `lvh-yard register static`

**What to build:** The Register page becomes the Static Instances page, with a list and a form. The CLI gets `register static`. Adding a Static Instance can publish a new Service. See `.scratch/navigation-and-register/spec.md`, user stories 4 to 14 and 35.

**Blocked by:** 02

**Status:** done

- [x] `POST /api/workstation/services/{service}/instances` takes an optional `publish` boolean.
- [x] If the Service did not exist before the call, the call publishes it with Local Scope. A `publish` of `false` stops this.
- [x] An existing Service keeps its Publication, or stays Unpublished.
- [x] `GET /static-instances` lists this Workstation's Static Instances, with Service, address, Health and a link to the edit form.
- [x] The page has a form for a Static Instance, with a "Publish a new Service" checkbox that is on by default.
- [x] The form has no kind choice and no container fields.
- [x] `GET /register` answers 301 to `/static-instances`.
- [x] `lvh-yard register static <service>` takes the static options of today's `register`, plus `--no-publish`.
- [x] Until ticket 05 is done, `lvh-yard register <service> --container ...` and its Compose options keep working.
- [x] `openapi.yaml` documents the `publish` field.
- [x] The user guide shows the Static Instances page and `register static`.
- [x] `StaticInstancesTest`, the Static Instances page test (from `RegisterPageTest`) and the CLI test cover these points.
