# 03: The CLI and the user guide show the three Services as ordinary Services

**What to build:** `lvh-yard list` and the user guide treat `yard`, `consul-ui` and `traefik-admin` as ordinary Services that the first start sets up. Nothing that the user sees names Core Services. See `.scratch/own-services/spec.md`, user stories 28 and 40.

**Blocked by:** 02

**Status:** done

- [x] `lvh-yard list` shows no "(Core Service)" mark.
- [x] The CLI does not read a `core` field from the dashboard.
- [x] The CLI tests for Core Service refusals go away.
- [x] The setup steps of the user guide name `yard`, `consul-ui` and `traefik-admin`, with their hostnames. They say that the first start sets them up and that you can remove them.
- [x] No other part of the user guide names Core Services.
- [x] The CLI test covers the text of `lvh-yard list`.
