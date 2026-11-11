# 01: Move the template files to `config/`

**What to build:** The files that `init` writes to a Workstation come from a `config/` folder at the repository root, not from the root `compose.yaml` and `.env.example`. A `dev/` compose file layers the dashboard `build` on the template, for people who build the image through Compose. Nothing changes for a user. This is the prefactor for the rest of the spec, so that later tickets add files to one place.

**Blocked by:** None (can start immediately)

**Status:** done

Spec: [../spec.md](../spec.md)

- [x] The root has no `compose.yaml` or `.env.example`. `config/` holds both.
- [ ] The mise task that embeds the templates in the CLI copies them from `config/`, and the byte-comparison unit test still passes.
- [x] The check task lists the `config/` files as sources, so a change to one of them starts a new run.
- [ ] Every test and script that read the root `compose.yaml` reads the new place, and `mise run check` passes.
- [ ] `mise run dev:up` still starts the two test Workstations, from the `dev/` compose file.
- [x] `docs/development.md` names the new places.

## Comments

The template task, template comparison test, init comment test and Traefik integration test now read `config/`. The development tasks layer `dev/compose.yaml` on that template, with the repository as the project directory so relative mounts and the build context stay in place.

Implementation is complete. The unchecked boxes need the integration owner's checks: `mise run check` and `mise run dev:up`. No tests, builds, linters or formatters ran in this ticket worktree, as assigned.
