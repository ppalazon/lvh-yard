# Local certificates and HTTPS on Windows: evidence

Real HTTPS, Client Certificate, Consul TLS, file-watching and privacy scenarios ran on the Windows VM against a
fresh Workstation. One product defect appeared: a hard link inside the Workstation Folder let the CLI rewrite the
ACL of a file outside it. It is fixed and has a regression test. This ticket also adds a Compose test for the
certificate-role mounts and the Windows certificate section of the user guide.

## Environment

Observed on 2026-11-15:

- Windows 11 Pro x64 VM `192.168.122.117`: 8 CPUs, 15.9 GB. Docker Desktop (as `yarddev`): 4 CPUs, 6.2 GB.
  The VM was used by this ticket alone. Every scenario ran once in Windows PowerShell 5.1 as the ordinary account
  `yarddev`. The second-user checks ran as the ordinary account `yardother` (both tokens non-administrator, as
  recorded in the shared notes).
- Merged `windows-11` (tip 555e740) before the run. `lvh-yard.exe` was cross-built on Linux
  (`GOOS=windows GOARCH=amd64 CGO_ENABLED=0`). The fixed executable has SHA-256 `8010CEEA...D505CF`.
- Workstation `win-native-07` (project `lvhy-win-07`), folder
  `C:\Users\yarddev\lvh-dev\07\O'Brien café 日本\workstation` (space, apostrophe, accents, CJK). It started
  from an empty folder and an empty `consul-data` volume. Ports 49400-49410. Images: Consul 2.1.0, Traefik
  v3.7.16, the ticket 07 dashboard image (no dashboard code changed since it was built), `lvh-probe:1`.
- The backend was a Python HTTP server in a host-network container on `127.0.0.1:49405`. It returns
  `https-backend-07`.
- The harness is a set of PowerShell scripts kept outside the repository. Raw outputs are in
  `~/.local/state/lvh-yard-windows-11/evidence/07-*.out`.
- Setup took about 45 s. The longest scenario took a few minutes. No stall was seen.

## 1. CA, certificate issue, import, enable and disable (native CLI)

The VM has no `openssl` and no shell.

- With HTTPS off, a connection to the HTTPS port 49404 was refused, and HTTP routing returned 200.
- `ws tls ca create` made the CA. A second `ws tls ca create` exited 1 with `a Yard CA exists: use --force to replace it`.
- `ws tls ca issue workstation win-native-07 --address 192.168.122.117` issued two certificates. The Traefik
  certificate names `*.lvh.me, localhost, 127.0.0.1, 192.168.122.117`. The Consul certificate names
  `server.win-native-07.consul, localhost, 127.0.0.1`.
- `ws set https-port 49404` then `ws tls import traefik --ca --cert --key` installed the files in `tls\traefik`
  and wrote `traefik\dynamic\10-tls.yaml`. The installed DER hash equalled the issued one.
- Names with a space, an apostrophe and accents worked: `tls ca issue user "josé@café.日本"`,
  `"o'brien@example.com"` and `"jane doe"` made `.p12` files. `Get-PfxData` opened each with its password.
  `tls import traefik` read its input files from the folder `ünï 'in puts` and installed them.
  `tls ca issue workstation other-07 --address ...` followed by `tls ca install other-07` wrote the hand-off folders under `certificates\other-07`.

## 2. HTTPS through Traefik, disable and re-enable

`ws compose recreate`, then a TLS 1.2 `SslStream` request with SNI `https07.lvh.me` to `127.0.0.1:49404`:

| Step                                  | Result                                                                                                                                                                          |
| ------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| HTTPS enabled, applied                | `200`, body `https-backend-07`. Served DER SHA-256 `78cfaae2...b5655` equals the issued and the installed file.                                                                 |
| Chain                                 | Builds to the Yard CA root, length 2. Root thumbprint equals the CA's.                                                                                                          |
| `curl.exe --cacert ca.crt`            | `https-backend-07 HTTP=200 verify=0`.                                                                                                                                           |
| `curl.exe` without that CA            | Failed: exit 35, `0x80096004 ... The signature of the certificate cannot be verified`.                                                                                          |
| `ws tls disable traefik`, no recreate | `10-tls.yaml` was deleted, `tls\traefik` files kept, `YARD_HTTPS_PORT=` emptied. Within the wait, Traefik served another certificate (`015e0772...`, its default), still `200`. |
| Disable applied (`compose recreate`)  | The HTTPS port refused connections. HTTP route `200`.                                                                                                                           |
| `ws tls enable traefik`               | Wrote `YARD_HTTPS_PORT=443` (not applied). `10-tls.yaml` was restored byte for byte. After `ws set https-port 49404` and recreate: `200` with DER `78cfaae2...` again.          |

## 3. Reload on NTFS bind mounts (real Traefik requests)

Files changed on the NTFS bind mount, with no container restart. Each wait polled a real request.

- YAML router `before07.lvh.me`: `200` after 1.6 s. The file was rewritten to `after07.lvh.me`: `200` after
  3.1 s, and the old host answered `404`. The file was deleted: `404` after 4.7 s, and the file was absent in
  the container.
- TOML router with a TLS router over HTTPS: same sequence. `tbefore07` `200`, then `tafter07` `200` and
  `tbefore07` `404`, then deletion gave `404` and the file was absent in the container.
- TLS-only change: `ws tls ca issue traefik win-native-07 --days 45` made a new certificate, and `ws tls import traefik`
  installed it. `10-tls.yaml` was byte-identical before and after (SHA-256 compared), and the container `StartedAt`
  did not change. The matching-SNI request returned the new DER `a4876e91...4afa` after 1.6 s (before:
  `78cfaae2...`), status `200`. The deletion of `10-tls.yaml` in section 2 is also a TLS-only change that Traefik
  applied by itself.

## 4. Client Certificate Route

`ws tls ca issue user alice --password ...` and `bob` (random password printed once) made `ca\clients\*.p12`
(1458 bytes). `alice.p12` holds one end-entity certificate (`CN=alice`, EKU Client Authentication) and the chain
`CN=LVH Yard CA`. A wrong password was rejected. A Route Profile `certificate` (`clientCertificate: {}`)
was published on `mtls07.lvh.me`.

- No client certificate: handshake failed (`The message received was unexpected or badly formatted`).
  Plain HTTP for that host: `404`.
- A self-signed `CN=mallory` certificate: handshake failed the same way.
- Documented import: `Import-PfxCertificate -CertStoreLocation Cert:\CurrentUser\My` printed `CN=alice`,
  `HasPrivateKey=True`. `SslStream` with that store certificate: `200`, `https-backend-07`.
  `curl.exe --cert "CurrentUser\MY\<thumbprint>"`: `https-backend-07 HTTP=200`. The same `curl.exe` without
  `--cert`: exit 56, `SEC_E_ILLEGAL_MESSAGE`. Bob, imported with the printed password: `200`.
- The unprotected Route `https07` on the same port answered `200` with no certificate.
- The harness removed the test certificates from the personal store: 0 left.
- `Import-Certificate -CertStoreLocation Cert:\CurrentUser\Root` over SSH failed with
  `UI is not allowed in this operation`, and nothing was added to the Root store. The `certmgr.msc` and
  double-click wizard routes, and the Firefox stores, were not run: they need a desktop session.

## 5. Consul certificate role (local)

`ws tls ca install win-native-07 --consul --traefik=false` wrote `tls\consul` and `consul.d\10-tls.hcl`; the
installed certificate DER equalled the issued one. After `ws compose recreate` the strict Status was `ok`,
Consul had a leader and `consul members` showed `win-native-07 alive`. The agent reported
`VerifyIncoming/VerifyOutgoing/VerifyServerHostname` true, from false before.

Real handshakes against the RPC port (TLS 1.3, Python client, Yard CA as trust):

- With the client certificate: handshake OK and the connection stayed open.
- Without it: handshake OK, then `TLSV13_ALERT_CERTIFICATE_REQUIRED` on the first read. TLS 1.3 reports the
  missing certificate after the handshake. Both the ALPN (`consul/rpc-multi`) and the plain variant behaved so.

With Consul TLS on, a new Service was created, registered and published: the Route answered `200` over HTTP, and
the existing Route answered `200` over HTTPS. `ws tls disable consul` removed `10-tls.hcl`, kept the
certificate files and, after recreate, the agent reported TLS off, Routes still `200`, and the plain RPC probe was
refused (connection reset). `ws tls enable consul` and recreate turned it on again, with the same handshake results as before.
Full mutual-TLS federation between Workstations belongs to ticket 08 and was not run.

## 6. Role isolation (running containers)

- Mounts: Consul has `consul.d`, `consul-data` and `tls\consul`. Traefik has `traefik\dynamic`, `tls\traefik`
  and its script. The dashboard has `lvh-yard.yaml`, `backups` and the Docker socket, and no certificate
  folder. No container mounts `ca\`.
- Traefik runs as uid 0 and sees `/source/tls` and `/etc/traefik/tls` with `ca.crt`, `traefik.crt` and `traefik.key`.
  `cat` of `consul.key` and `ca.key` at seven plausible paths (including `../ca/ca.key`) failed with
  `No such file or directory`. The SHA-256 of every file Traefik can see (`find -L`) matched none of
  `ca.key`, `consul.key` or the client export, and matched its own `traefik.key`.
- Consul (uid 1000) sees `ca.crt`, `consul.crt`, `consul.key`. It could not read `traefik.key` or `ca.key`. No visible hash matched
  `ca.key`, `traefik.key` or the client export.
- The dashboard container has no file named `ca.key`, `*.p12`, `consul.key` or `traefik.key`.
- The same checks passed again after the fix in section 8.

## 7. Windows ACLs and a second ordinary user

- Owner view (`icacls`): `ca`, `ca\ca.key`, `ca\clients`, `*.p12`, issued keys, `tls\*\*.key`, `.env` and
  `consul.d\10-tls.hcl` allow only `yarddev`, SYSTEM and Administrators (full control, inherited
  by children). `Get-Acl` showed `AreAccessRulesProtected=True` on `ca.key`, `alice.p12` and `consul.key`.
- `yardother`: a control file that grants `Users` read was readable, so each refusal comes from the ACL. Reads
  of 16 files (`.env`, `ca.key`, `ca.crt`, both `.p12`, issued and installed keys and certificates, `10-tls.hcl`,
  `10-tls.yaml`, `compose.yaml`, `lvh-yard.yaml`) and listing of four folders (`ca`, `ca\clients`, `tls\consul`,
  `backups`) all gave `UnauthorizedAccess`. Writes into `ca` and `tls\traefik` were denied. The same run passed before and after the fix.
- Runtime access: Consul (uid 1000) read its key and certificates. Traefik read its key from the source mount
  and from the staged copy. `POST /backups/new` through the dashboard returned `303` and a Backup file
  appeared in `backups`.

## 8. Invalid names, links and junctions

Invalid output filenames: `tls ca issue user NAME --password x` exited 1 for 22 values: `bad:name`, `CON`, `con.txt`,
`NUL`, `aux.x`, `COM1`, `lpt9.log`, `alice.`, `trail ` (trailing space), `a|b`, `a*b`, `a?b`, `a"b`, `a<b`, `a>b`, a
name with a bell character, `..\escape`, `../escape`, `sub\dir`, `x/y`, `..` and `.`. The message names the rule. No file appeared in
`ca\clients` or beside the Workstation Folder. `tls ca issue workstation` with `bad:name` and `..\x` exited 1.
`--dir` with `con\x`, `bad:dir` and `trailing.\ws` exited 1 (the folder does not exist).

Junctions in a second, stopped Workstation Folder. The target `outside-target` held only `victim.txt`:

| Junction at                    | Command                    | Result                                                                               |
| ------------------------------ | -------------------------- | ------------------------------------------------------------------------------------ |
| `ca\clients`                   | `tls ca issue user`        | exit 1, `openat ca/clients: ... untrusted mount point`. Target unchanged.            |
| `ca\certs`                     | `tls ca issue workstation` | exit 1, same message. Target unchanged.                                              |
| `ca` (whole)                   | `tls ca create --force`    | exit 1, same message. Target unchanged.                                              |
| `tls\traefik`                  | `tls ca install --traefik` | exit 1, same message. Target unchanged.                                              |
| `traefik\dynamic`              | `tls enable traefik`       | exit 1, same message. Target unchanged.                                              |
| a junction in the `--dir` path | `tls ca create --force`    | exit 1, `The path cannot be traversed because it contains an untrusted mount point`. |

A symbolic link could not be tested: `mklink /D` as the ordinary user failed with
`You do not have sufficient privilege to perform this operation`. The unit tests that use symlinks skip on Windows.

### Defect: a hard link let the CLI rewrite the ACL of an outside file

Setup: the outside folder grants `Users` read to its files, and `ca\clients\hard.txt` is a hard link to
`outside-target\victim.txt`. `tls ca issue user erin` exited 1 with `cannot protect a file with multiple hard
links`, and the file was not rewritten. But the outside file's ACL changed:

```
BEFORE: BUILTIN\Users:(I)(R)  SYSTEM:(I)(F)  Administrators:(I)(F)  yarddev:(I)(F)
AFTER:                        yarddev:(I)(F) SYSTEM:(I)(F)  Administrators:(I)(F)
```

Cause: `protectFile` set the folder's DACL with `SetSecurityInfo`. Windows pushes a changed DACL into the
children that inherit it, and the hard link is such a child. Fix: `SetKernelObjectSecurity` sets the DACL with
no propagation. Every entry is protected by the walk itself.

Regression test `TestWindowsProtectionLeavesAHardLinkedOutsideFileAlone` (native, `yarddev`):

- Against a build without the fix it failed: the outside DACL lost `(A;ID;FR;;;BU)`.
- Against the fixed build it passed. `TestWindowsProtectionDeniesAnotherUserByEffectiveAccess` passed on both.

After the fix, on the live Workstation: `ws tls ca issue user zed` (it protects the whole folder),
`ws compose recreate`, strict Status `ok`, HTTPS `200` with the same DER as the installed file, the isolation
and owner-ACL checks (sections 6 and 7) and the `yardother` denial all passed again. In the second
Workstation Folder the same hard-link scenario left the outside ACL unchanged (`True`).

## 9. Documentation

The user guide has a Windows section for trusting the CA (`Import-Certificate` to `Cert:\CurrentUser\Root`,
`certmgr.msc`, Firefox's own store, `curl.exe --cacert`) and for importing a Client Certificate
(`Import-PfxCertificate`, the import wizard, `curl.exe --cert "CurrentUser\MY\<thumbprint>"`, removal). The
PowerShell, `curl.exe` and removal commands were run as written above. The `certmgr.msc`, wizard and Firefox
routes were not run.

## Tests

- `TestComposeMountsKeepCertificateRolesApart` parses `compose.yaml` and `compose.windows.yaml`: Consul mounts
  only `./tls/consul` read-only, Traefik only `./tls/traefik` read-only, the dashboard none, and nothing mounts
  `./ca`.
- `mise run test:unit` and `mise run check` pass on the merged result (including `GOOS=windows` lint).

## Not covered

- Full mutual-TLS federation with another Workstation (ticket 08).
- Symbolic links created by an administrator, and the desktop certificate dialogs.
- Windows PowerShell 7: the scenarios ran in Windows PowerShell 5.1 only.
