# 07: Use local certificates and HTTPS on Windows

**What to build:** A Windows developer uses local Yard CA and certificate commands, turns TLS on or off, serves HTTPS, and exports Client Certificates securely. NTFS-backed routing and TLS changes reach Traefik without weakening secret isolation.

**Blocked by:** 03: Set up a private local Windows Workstation.

**Status:** ready-for-agent

- [ ] Local Yard CA creation, certificate issue/import and TLS enable/disable work through the native CLI without Unix crypto tools or shells.
- [ ] Enable HTTPS for the Workstation and successfully request a published Service using the expected certificate. Disable and re-enable it through the supported workflow and observe the corresponding behavior.
- [ ] Enable the Consul certificate role locally without breaking the Workstation. Full mixed-platform mutual TLS federation is completed by ticket 08.
- [ ] A Route requiring a Client Certificate rejects a request without one and accepts a valid one. Client certificate export produces an artifact usable through the documented manual import workflow.
- [ ] Protect CA keys, identity keys and client exports with effective Windows ACLs. A second ordinary user cannot read them, while runtime containers retain their required access.
- [ ] Traefik cannot read Consul's identity key or the Yard CA private key. Verify certificate-role isolation in the running setup.
- [ ] Invalid output filenames and escaping reparse points fail safely during certificate operations. Paths with spaces, apostrophes and Unicode remain usable.
- [ ] Update routing and TLS configuration on NTFS bind mounts and observe the changed behavior through real Traefik requests. Do not substitute file-write success for reload proof.
- [ ] Document manual browser trust and Client Certificate import. Record native Windows HTTPS, export, privacy and file-watching results.
