# 13: The Client Certificate Route Profile

**What to build:** A Route Profile can require a Client Certificate from the Yard CA. A Publication that uses it answers on HTTPS only, and a request without a certificate is refused. It never falls back to an open HTTP Route. The user adds the profile through the Route Profiles page, the API, or `lvh-yard profiles`.

**Blocked by:**

- 11: Traefik HTTPS
- 12: `ws tls client create`

**Status:** ready-for-agent

Spec: [../spec.md](../spec.md)

- [ ] A Route Profile of the new kind has no Traefik middleware. It is written with its own reserved key, and the Route Profiles page, the profile API and `lvh-yard profiles` accept it, with the OpenAPI file updated and the CLI parity test green.
- [ ] A Route with the profile has one `websecure` router that uses the named TLS option. It has no `web` router.
- [ ] A chain that names the profile gives the Route the same requirement.
- [ ] A Workstation with no HTTPS port gives no Route to a Publication with the profile.
- [ ] On the Home Workstation, the Service Warning for a Publication that its own Workstation cannot serve this way has the level error.
- [ ] Component tests cover the Routes, the chain, the missing HTTPS port and the Service Warning, and the profile saved through the page, the API and the profile files.
- [ ] The integration test refuses a request without a certificate, and accepts one with a certificate from `ws tls client create`.
- [ ] `GLOSSARY.md` and `docs/user-guide.md` describe the profile.
