---
status: accepted
---

# One Yard CA, and a Consul and a Traefik certificate for each Workstation

A Yard has one CA. It signs a Consul certificate for each Workstation, and it can sign a Traefik certificate and Client Certificates for people. Consul uses its certificate for RPC between Workstations (`verify_incoming` and `verify_outgoing`). Traefik uses its own for HTTPS on its Routes. The two have separate keys. The Consul certificate holds the name `server.<workstation>.consul` and both the server and client key usages. The datacenter of each Workstation is its own name (see [ADR-0001](0001-one-consul-datacenter-per-workstation.md)), and Consul checks that name against the certificate. The Traefik certificate holds `*.<Base Domain>` and the names of remote Routes, and only the server usage, because browsers need no more.

The jobs differ in who must sign and when they change. Consul accepts only the Yard CA, and its certificate never changes when a Workstation joins. The Traefik certificate faces browsers, so a company CA or a public CA can sign it, and it changes with the Routes that it covers. Compose mounts `tls/consul/` only in Consul and `tls/traefik/` only in Traefik, so a process that faces browsers never holds the key that identifies the Workstation to the other Consul servers. Each folder has its own copy of `ca.crt`. Traefik checks Client Certificates with it, so it is always the Yard CA, even when a public CA signed the Traefik certificate.

The CA key stays on the Workstation that made it, in a file with mode 600. Every command that signs sits under `ws tls ca`, because it needs that key. `ws tls ca issue workstation <name>` keeps what it signs in `ca/certs/<name>/consul.crt|key` and `traefik.crt|key`, one pair for each role of each Workstation. `ws tls ca issue consul` and `ws tls ca issue traefik` sign one. `ws tls ca install <name>` puts the pairs in `tls/` for this Workstation, or in folders to copy for another one, which can be copied into its `tls/` through scp. `ws tls import <consul|traefik>` installs a certificate that someone else signed: a Yard CA owner, a company CA or a public CA. It checks the Consul certificate against the Yard CA, and the Traefik certificate against the Yard CA or the public ones. The Gossip Key is a copied secret, and a CA key must not be one: with the CA key, anyone can sign a certificate for any Workstation or person.

Installing a certificate turns its service on: Consul mutual TLS, or Traefik HTTPS. `ws tls enable` and `ws tls disable` change that later and keep the files. A Yard may want mTLS between Workstations and no HTTPS, or the other way round.

A Client Certificate is required by a Route Profile option, for the Publications that use it, and not by the whole entry point. A requirement on the entry point would also lock the `yard` Route, and the dashboard has no login. Traefik applies a client-certificate rule per hostname, so one Route Profile option fits it.

## Considered Options

- **One CA for each Workstation**: rejected. Every Workstation would need to trust every other CA for Consul federation, and the Yard would hold as many CAs as Workstations.
- **One certificate for both jobs**: first chosen, then rejected. It carried fewer files, but it forced every Workstation to reissue the Consul identity when a Route name changed, it put the Consul key in the Traefik container, and it tied HTTPS to the Yard CA, so no public certificate could serve the Routes.
- **Let's Encrypt as a file**: not done. Traefik can order and renew public certificates itself, so that belongs to Traefik's ACME resolver and not to copied files. `ws tls import traefik` takes a public certificate that you obtained.
- **Make the browser trust the CA**: not done. The browser differs on each computer, so `ws` only prints how to do it.

## Consequences

- If the Workstation with the CA key is lost, nothing breaks until a certificate expires. Then you make a new CA, and every Workstation installs new certificates.
- Renaming a Workstation does not exist (`ws` has no `rename`), because the name is in the certificate, in the Consul datacenter, and in every hostname.
- A Workstation Folder from before this decision mounts `./tls` in both containers. `ws compose upgrade` says to mount `./tls/consul` and `./tls/traefik`, and the certificates are installed again. This is the one step of the change.
- Traefik to Instances over mutual TLS with the Yard CA is not part of this decision. It would add a third certificate: a client certificate that Traefik presents to the Instances, which differs from the server certificate that browsers see. The role folders leave room for it.
