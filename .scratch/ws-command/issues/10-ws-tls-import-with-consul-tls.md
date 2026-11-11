# 10: `ws tls import`, with Consul TLS

**What to build:** `ws tls import --ca <file> --cert <file> --key <file>` makes this Workstation use a Workstation Certificate that LVH Yard or the company CA made. Consul then requires TLS and a verified certificate for RPC with other Workstations, and two Workstations federate over it. This is the first step of the integration test.

**Blocked by:**

- 06: Consul fixed options in `consul.d/`, and generated files
- 09: `ws tls ca create` and `ws tls cert create`

**Status:** done

Spec: [../spec.md](../spec.md)

- [x] `ws tls import` refuses a key that does not match its certificate, a certificate that the CA did not sign, and a certificate without `server.<this Workstation>.consul`. It names the problem and writes nothing.
- [x] It copies the files into the folder with mode 600 for the key, and writes `consul.d/10-tls.hcl`. Consul requires TLS and verifies the certificate in both directions, and checks the server name against the datacenter. The HTTP API stays plain HTTP on loopback.
- [x] `ws upgrade` rewrites `consul.d/10-tls.hcl` the same way as the other generated files.
- [x] The command warns that every Workstation of the Yard needs TLS, and says that a Workstation without it cannot federate.
- [x] The integration test makes two Workstations with `ws init`, makes a CA and certificates with `ws tls`, imports them, and starts both. The Consuls federate, and the dashboard of each sees the other.
- [x] CLI unit tests cover the refusals and the written files. `docs/user-guide.md` says to turn TLS on everywhere in one go.
