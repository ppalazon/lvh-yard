# 06: Upgrade notes for an old `route-profiles.yaml`

**What to build:** A user who upgrades learns that valid profiles stay, and how to bring back a refused one. See `.scratch/route-profiles-in-kv/spec.md`, user story 42.

**Blocked by:** 02

**Status:** done

- [ ] `docs/user-guide.md` says that valid profiles stay in Consul KV after the upgrade.
- [ ] It says that a profile that the old dashboard refused did not reach Consul KV.
- [ ] It shows how to apply an old `route-profiles.yaml` once with `lvh-yard profiles set`.
- [ ] It says that the old file can then be deleted.
