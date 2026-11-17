---
status: accepted
---

# Traefik TLS has three independent parts

Traefik uses TLS for three jobs that have different owners and different lifetimes: HTTPS for browsers, the check of the Client Certificates of people, and mutual TLS to the Services. Before this decision, one certificate and one `ws tls install` command turned on the first two together, and the third did not exist. This replaces the Traefik commands of [ADR-0016](0016-each-workstation-has-its-own-ca-and-one-is-the-main-ca.md): `ws tls ca issue traefik`, `ws tls install`, `ws tls enable` and `ws tls disable`. The rest of ADR-0016 stays.

Each job is a TLS Part with its own command group, files, generated Traefik file and `.env` setting:

| Part                              | Group           | Files in `tls/traefik/`               | Generated file in `traefik/dynamic/` | On when                         |
| --------------------------------- | --------------- | ------------------------------------- | ------------------------------------ | ------------------------------- |
| HTTPS                             | `ws tls https`  | `https.crt`, `https.key`              | `10-tls.yaml`                        | `YARD_HTTPS_PORT` has a value   |
| Client Certificate authentication | `ws tls client` | `client-ca.crt`                       | `11-client-auth.yaml`                | `YARD_CLIENT_AUTH_ENABLED=true` |
| Mutual TLS to Services            | `ws tls mtls`   | `mtls.crt`, `mtls.key`, `mtls-ca.crt` | `12-mtls.yaml`                       | `YARD_MTLS_ENABLED=true`        |

Every group has `create`, `import`, `enable` and `disable`. `create` uses the Workstation CA, signs what the part needs, and turns the part on. `import` takes a CA, a certificate and a key that you bring, checks them before it writes, and turns the part on. `disable` keeps the files, so `enable` brings the part back. The Workstation CA signs through one function, which `ws tls ca issue service` and the `create` commands share. `ws tls ca issue` is where you sign for a Service or a person. There is no `ca issue traefik`: Traefik's own certificates belong to the part that uses them.

Traefik merges the files of its file provider, so each part writes its own file and the parts never edit each other's. The dashboard cannot read these files. It reads the two new settings from its environment, as it reads `YARD_HTTPS_PORT`, so it gives no Route to a Service that needs a part that is off.

Mutual TLS to Services applies by Route Profile, as the Client Certificate does ([ADR-0013](0013-one-yard-ca-and-two-certificates-per-workstation.md)). A reserved option `mutualTLS: {}` makes the Route call the Instances over `https://` with the Traefik ServersTransport `yard-mtls`. Other Routes keep plain HTTP. Traefik checks the Service with `mtls-ca.crt` and shows `mtls.crt`. The Service has its own CA to check Traefik. `ws tls ca issue service <name>` signs a certificate that serves HTTPS and names the Instance address.

## Considered Options

- **One switch for all of Traefik**: rejected. A Yard may want HTTPS and no client check, or client checks on a company CA while a public CA signs HTTPS.
- **Mutual TLS for every Route when it is on**: rejected. One Service without TLS would break, because Instances have only an address and a port.
- **A ServersTransport only, attached by hand**: rejected. The user would write Traefik files that LVH Yard overwrites, and no Service Warning could say that a Route lacks it.
- **One folder for each part**: not done. Compose mounts `tls/traefik/` only in Traefik already, and the three parts share that one trust boundary.

## Consequences

- `https import` checks the chain of the certificate when it imports. The CA that signed it is not kept, so `https enable` checks only the key, the dates and the Yard hostname again.
- A folder from before this decision has `traefik.crt`, `traefik.key` and `ca.crt`. `ws compose upgrade` renames them to `https.crt`, `https.key` and `client-ca.crt`, and turns the client check on when HTTPS was on, because HTTPS used to turn it on.
- Client Certificate authentication is off after `https create` until `client create` runs. A Route with `clientCertificate: {}` has no Route, and a Service Warning, until then.
- The CA that checks the Services (`mtls-ca.crt`) may differ from the one that signed Traefik's certificate, as it does in a company with its own CA.
- The dashboard does not check that an Instance speaks TLS. A Reachability test of an Instance stays a plain TCP connection.
