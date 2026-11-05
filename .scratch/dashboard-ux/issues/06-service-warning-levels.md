# 06: Service Warning levels in the API and the CLI

**What to build:** Each Service Warning has a level, `error` or `risk`. The API sends it, and `lvh-yard list` and `lvh-yard publish` print it before each message. See `.scratch/dashboard-ux/spec.md`, user stories 53 to 57.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] Each Service Warning has a required `severity` field with the JSON value `error` or `risk`.
- [ ] `unknown-route-profile` is `error`. `loopback-instance`, `unpublished-container-port` and `route-profile-auth` are `risk`.
- [ ] The API does not add the Unavailable or Unhealthy state to `warnings`.
- [ ] The OpenAPI document shows the field and its two values.
- [ ] `lvh-yard list` and `lvh-yard publish` print each Service Warning as `error: <message>` or `risk: <message>`.
- [ ] `docs/user-guide.md` explains the two levels and the new CLI output.
- [ ] Dashboard tests cover the level of each kind. The OpenAPI test covers the field.
- [ ] CLI tests cover both prefixes in `list` and in `publish`.
