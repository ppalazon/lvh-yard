# 03: lvh-yard init --upgrade changes only image versions

**What to build:** After installing a newer CLI, `lvh-yard init --upgrade` moves the `consul`, `traefik` and `dashboard` images in the existing `compose.yaml` to this version's, and changes nothing else.

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] Only the `image:` values of the three services change; every other line stays byte for byte
- [ ] `.env` and `route-profiles.yaml` are untouched
- [ ] Each changed image is printed, from and to
- [ ] An image line that is not found is reported, and that file is left as it is
- [ ] `--upgrade`, `--reinit` and `--force` cannot be combined
