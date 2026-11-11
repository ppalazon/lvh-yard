# 09: `ws tls ca create` and `ws tls cert create`

**What to build:** One Workstation makes the Yard CA and signs a Workstation Certificate for itself and for the others, with no `openssl`. A certificate for another Workstation goes to a folder that the user copies. The CA key never leaves the Workstation that made it, and it sits where no container mounts it ([ADR-0013](../../../docs/adr/0013-one-yard-ca-and-one-certificate-per-workstation.md)). Nothing starts using TLS yet.

**Blocked by:** 05: Private Workstation Folder, and Consul as your user

**Status:** done

Spec: [../spec.md](../spec.md)

- [x] `ws tls ca create` writes a CA certificate and key, ECDSA P-256, valid 10 years. The key has mode 600 and sits outside every folder that Compose mounts.
- [x] `ws tls ca create` refuses when a CA exists, unless `--force` is given.
- [x] `ws tls cert create --workstation <name>` writes a certificate, a key and the CA certificate to a folder named for the Workstation. The certificate has the names `server.<name>.consul`, `localhost`, `127.0.0.1`, each `--address` given, and `*.<Base Domain>`. It has the server and the client key usage, and it is valid 1 year.
- [x] `--base-domain` defaults to this Workstation's. `--days` changes the validity of a CA or a certificate.
- [x] With no `--workstation`, the command signs for this Workstation and installs the files in its folder, with mode 600 for the key.
- [x] The command fails with a clear message on a Workstation that has no CA key.
- [x] CLI unit tests read the files back with `crypto/x509`: the chain, the names, the key usage and the validity. They also check the key modes and the refusals.
- [x] `docs/user-guide.md` explains the Yard CA and how to make a browser trust it.
