# 01: Read and check route-profiles.yaml

**What to build:** The user writes Traefik middlewares in `route-profiles.yaml` and sees them listed, with any error, on the dashboard and in `lvh-yard profiles`. See `.scratch/route-profiles/spec.md` and ADR-0004.

**Blocked by:** init/01

**Status:** ready-for-agent

- [x] The dashboard reads the file from `YARD_ROUTE_PROFILES` (default: `init`'s folder); no file means no profiles
- [x] The `init` template writes a commented example file, and `compose.yaml` mounts it read-only into the dashboard
- [x] Changes are picked up without a restart
- [x] Refused: unknown middleware types, `usersFile`, `forwardAuth` to an out-of-range address or `localhost`, plugins, chains naming middlewares outside the file; other profiles still apply
- [x] Valid profiles are copied to this Workstation's Consul KV; removed ones are deleted
- [x] `GET /api/workstation/route-profiles` lists names, types and errors, in `openapi.yaml`; `lvh-yard profiles` prints them
