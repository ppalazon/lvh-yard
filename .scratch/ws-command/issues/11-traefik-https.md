# 11: Traefik HTTPS

**What to build:** Once a Workstation has a Workstation Certificate, each of its Routes answers on HTTP and on HTTPS, with no redirect. HTTPS is off until `ws tls import` turns it on.

**Blocked by:**

- 10: `ws tls import`, with Consul TLS
- 04: `ws show`, `ws set` and `ws join`

**Status:** done

Spec: [../spec.md](../spec.md)

- [x] The HTTPS port setting exists in `.env`, empty by default. `ws set` knows it. `ws tls import` sets it to 443.
- [x] When the HTTPS port is set, Traefik gets a `websecure` entry point on the Traefik bind address and that port. When it is empty, Traefik has no such entry point, and nothing listens on 443.
- [x] `ws tls import` writes `traefik/dynamic/10-tls.yaml`, with the Workstation Certificate, a default TLS option, and one named TLS option that requires a Client Certificate signed by the Yard CA. The option name is `yard-client-certificate@file`.
- [x] The dashboard learns the HTTPS port through its own setting. With it set, each Route gets two Traefik routers: one on `web`, and one on `websecure` with TLS. Without it, the Route is as before.
- [x] A component test checks both routers. The integration test shows that a Route answers on HTTPS with a certificate that the Yard CA signed, and still answers on HTTP.
- [x] With Traefik off, none of this is written or used.
- [x] `docs/user-guide.md` describes HTTPS.
