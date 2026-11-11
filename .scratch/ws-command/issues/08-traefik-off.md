# 08: Traefik off

**What to build:** On a shared host with no direct users, `ws set traefik off` stops Traefik from starting, and the rest works as usual. Consul and the dashboard run and publish Instances to the Yard. The dashboard knows Traefik is off through its own setting, so Status does not show a failing Traefik, and the first setup does not register `traefik-admin`.

**Blocked by:** 04: `ws show`, `ws set` and `ws join`

**Status:** done

Spec: [../spec.md](../spec.md)

- [x] The Traefik service is in the Compose profile `traefik`, and `ws init` sets `COMPOSE_PROFILES` to `dashboard,traefik`.
- [x] A dashboard setting says whether Traefik is on. It is true by default, and compose passes it to the container. A dashboard run from the sources reads the same setting.
- [x] `ws set traefik off` writes that setting and removes `traefik` from `COMPOSE_PROFILES` in one step, and keeps the other profiles. `ws set traefik on` reverses both.
- [x] With Traefik off, Status has no Traefik Status Part, so the Workstation is not failing because of Traefik.
- [x] With Traefik off, a Reachability test through a Route says that this Workstation has no Traefik. A direct test still works.
- [x] With Traefik off, the first setup registers `yard` and `consul-ui` but not `traefik-admin`, and the setup marker rules of ADR-0006 stay.
- [x] `ws set traefik off` prints `lvh-yard remove traefik-admin` for a Workstation that has the Service.
- [x] Component tests cover Status, Reachability and the first setup.
- [ ] The integration test starts a Workstation with Traefik off, and Traefik is not running. Ticket 10 adds this to the shared real-Docker test.
- [x] `GLOSSARY.md` stays correct, and `docs/user-guide.md` has a section on a host without Traefik.
