---
status: accepted
---

# One Yard CA, and one Workstation Certificate for each Workstation

A Yard has one CA. It signs one Workstation Certificate for each Workstation, and Client Certificates for people. Consul uses the Workstation Certificate for RPC between Workstations (`verify_incoming` and `verify_outgoing`), and Traefik uses it for HTTPS on its Routes. One certificate does both jobs, so it holds the names `server.<workstation>.consul` and `*.<Base Domain>`, and both the server and client key usages. The datacenter of each Workstation is its own name (see [ADR-0001](0001-one-consul-datacenter-per-workstation.md)), and Consul checks that name against the certificate.

The CA key stays on the Workstation that made it, in a file with mode 600. `ws tls cert create --workstation <name>` writes a bundle for another Workstation, and you copy it over and run `ws tls import` there. A CA that you bring yourself goes through the same import. The Gossip Key is a copied secret, and a CA key must not be one: with the CA key, anyone can sign a certificate for any Workstation or person.

A Client Certificate is required by a Route Profile option, for the Publications that use it, and not by the whole entry point. A requirement on the entry point would also lock the `yard` Route, and the dashboard has no login. Traefik applies a client-certificate rule per hostname, so one Route Profile option fits it.

## Considered Options

- **One CA for each Workstation**: rejected. Every Workstation would need to trust every other CA for Consul federation, and the Yard would hold as many CAs as Workstations.
- **A separate certificate for Consul and for Traefik**: rejected for now. It doubles the files to carry to each Workstation, and gains nothing while one Yard CA signs both.
- **Make the browser trust the CA**: not done. The browser differs on each computer, so `ws` only prints how to do it.

## Consequences

- If the Workstation with the CA key is lost, nothing breaks until a certificate expires. Then you make a new CA, and every Workstation imports a new certificate.
- Renaming a Workstation does not exist (`ws` has no `rename`), because the name is in the certificate, in the Consul datacenter, and in every hostname.
- Traefik to Instances over HTTPS with the Yard CA is not part of this decision. It comes later.
