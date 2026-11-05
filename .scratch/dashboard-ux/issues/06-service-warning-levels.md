# 06: Service Warning levels in the API and the CLI

**What to build:** Each Service Warning has a level, `error` or `risk`. The API sends it, and `lvh-yard list` and `lvh-yard publish` print it before each message. See `.scratch/dashboard-ux/spec.md`, user stories 53 to 57.

**Blocked by:** None (can start immediately).

**Status:** done

- [x] Each Service Warning has a required `severity` field with the JSON value `error` or `risk`.
- [x] `unknown-route-profile` is `error`. `loopback-instance`, `unpublished-container-port` and `route-profile-auth` are `risk`.
- [x] The API does not add the Unavailable or Unhealthy state to `warnings`.
- [x] The OpenAPI document shows the field and its two values.
- [x] `lvh-yard list` and `lvh-yard publish` print each Service Warning as `error: <message>` or `risk: <message>`.
- [x] `docs/user-guide.md` explains the two levels and the new CLI output.
- [x] Dashboard tests cover the level of each kind. The OpenAPI test covers the field.
- [x] CLI tests cover both prefixes in `list` and in `publish`.
