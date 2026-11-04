# 01: Status has three parts: Consul, Traefik and Docker

**What to build:** The status of this Workstation shows Consul, Traefik and Docker, and no Core Services part. Consul and Traefik are read at the addresses from the environment variables, so the status does not depend on any Service. See `.scratch/own-services/spec.md`, user stories 22 to 27.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] `GET /api/status` has the parts Consul, Traefik and Docker, and no Core Services part.
- [x] The status page shows the same three parts. Its text says whether LVH Yard works on this Workstation.
- [x] The header of every page shows one dot for each of the three parts.
- [x] The summary of the status API in the API documentation says whether LVH Yard works on this Workstation.
- [x] The help of `lvh-yard status` is "Shows whether LVH Yard works on this Workstation."
- [x] `lvh-yard status` exits with 1 only when Consul, Traefik or Docker is not OK.
- [x] The last next step of `lvh-yard init` is "Make sure that LVH Yard works: lvh-yard status".
- [x] The status lines of the user guide name no Core Services.
- [x] The status test, the status page test and the CLI test cover these points.
