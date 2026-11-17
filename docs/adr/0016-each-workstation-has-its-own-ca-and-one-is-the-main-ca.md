---
status: accepted
---

# Each Workstation has its own CA, and one of them is the main CA

[ADR-0018](0018-traefik-tls-has-three-independent-parts.md) replaces the Traefik commands below: `ws tls ca issue traefik`, `ws tls install`, `ws tls enable` and `ws tls disable` are now `ws tls https`, `ws tls client` and `ws tls mtls`.

`ws tls ca create` makes one CA for the Workstation Folder, in `ca/ca.crt` and `ca/ca.key`. That is the Workstation CA. There is no second CA, no CA with a chosen file name, and no CA above it. This replaces the single Yard CA of [ADR-0013](0013-one-yard-ca-and-two-certificates-per-workstation.md).

Consul and Traefik need different things from a CA, so they use it in different ways:

- **Consul** needs every Workstation of a Yard to trust the same CA. A Yard picks one Workstation CA as the main CA, and it does not matter which. `ws yard share` signs a Consul certificate for each joining Workstation with it, and carries it in the encrypted invitation. `ws yard join` installs it. On the main CA's own Workstation, the first `ws yard share` installs its own Consul certificate.
- **Traefik** is private to each Workstation. `ws tls ca issue traefik` signs the Traefik certificate for this Workstation only, with no name argument, and `ws tls install` installs it. The key never leaves the Workstation, and no command packs a Traefik certificate for another Workstation. The Client Certificates of its people come from the same CA, and Traefik checks them with its own `tls/traefik/ca.crt`.

The CLI has one command for each service, and none for a whole Workstation. `ws tls ca issue workstation`, `ws tls ca export workstation` and `ws tls ca install` with no service are gone. So are the Consul versions of `ws tls ca issue`, `ws tls ca export`, `ws tls install` and `ws tls enable|disable`: `ws yard share` and `ws yard join` sign, carry and install the Consul certificate. The Traefik commands take no service name. `ws tls install` sits under `ws tls`, not under `ws tls ca`, because it needs no CA key. Without flags it installs what this Workstation's CA signed for this Workstation. With `--ca`, `--cert` and `--key` together, it installs the files that you name, such as a company or public certificate. Both ways run the same checks. This replaces the separate `ws tls import`, which did the second job.

A more complex setup is made by hand: an intermediate CA, a CA for Traefik to its Instances, a company CA or a public CA. `ws tls install --ca --cert --key` takes any Traefik certificate, and checks it before it writes. For Consul, no command takes a certificate of your own: edit `tls/consul/` and `consul.d/` yourself ([ADR-0017](0017-a-yard-is-shared-with-an-encrypted-invitation.md)).

## Considered Options

- **A Yard CA that signs a Workstation CA for each Workstation**: not done. It needs a request and signing flow between Workstations, name constraints on each Workstation CA so that its holder cannot sign a Consul certificate, and a way for every Traefik to trust the Workstation CAs of the others. A test showed that without name constraints, the holder of an intermediate key can mint a Consul certificate that Consul accepts. That is too much machinery for the first use of the CA, so it is left to a manual setup.
- **A chosen file name for each CA (`--ca-file`)**: built, then removed. Two CAs in one folder, one for the Yard and one for the Workstation, is the common case, and this decision gives each its own Workstation Folder instead. Every command that signs, installs or exports would otherwise need the name, and each output path would need a CA part.
- **Install a whole Workstation at once**: removed. Consul, Traefik HTTPS and Client Certificates have different owners and different lifetimes, so each is installed on its own.

## Consequences

- A Client Certificate works on the Routes that its own Workstation serves, because Traefik checks it with that Workstation's CA. A Route Profile with `clientCertificate: {}` on a Publication that another Workstation routes asks that Workstation's Traefik, which does not know the CA. To serve it, give that Workstation the same CA for Traefik with `ws tls install traefik --ca`, or sign the person a certificate on that Workstation.
- Until a Workstation has a Consul certificate from the main CA, Consul TLS stays off there. A Workstation that has its own CA but is not the main CA never installs its own Consul certificate.
- A stolen Workstation CA key signs for that Workstation's Traefik and Client Certificates, and, on the main CA's Workstation, for Consul. Putting the main CA on a Workstation that few people use is the user's choice.
- An old Workstation Folder keeps its `ca/ca.crt`, `ca/ca.key` and `ca/certs/`. It was the Yard CA, and it is now the main CA of that Yard. Its Traefik certificate, signed by that CA, keeps working. Other Workstations of the Yard keep their Consul certificates.
