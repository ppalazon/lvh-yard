# TLS Yard with manual certificate handoff: evidence

A real Windows 11 Workstation and a real Linux Workstation joined one Yard with Consul mutual TLS twice. Each
time a different one held the Yard CA, and the certificates moved by hand. Both ran real Yard Publication
requests in both directions. The native Windows CLI refused an automated transfer before any change. A Unix
sender found the Windows destination before it ran any preparation command. Both refusals left both computers
as they were.

Two gaps in the product code were found by component tests that failed first (see
[Defects found](#defects-found)). The fixes and their tests are in this branch.

## Environment

Observed on 2026-11-15:

- Windows 11 Pro x64 VM `192.168.122.117`, ordinary account `t-x\yarddev` (token: Medium Mandatory Level, not
  administrator). Windows PowerShell 5.1.26100. Docker Desktop (as `yarddev`): 4 CPUs, 6 GB; engine 29.9.3.
  The other stacks on the Docker VM were stopped when I listed its containers. No stall was seen: every command
  answered within seconds.
- `lvh-yard.exe` cross-built from this branch (`GOOS=windows GOARCH=amd64 CGO_ENABLED=0`, version `dev`).
  SHA-256 `a6b83747c49cedeb...ea52c`. The Linux CLI was built from the same commit.
- Windows Workstation `win-yard-08`, project `lvhy-win-08`, folder `C:\Users\yarddev\lvh-dev\08\workstation`,
  address `192.168.122.117`, ports 49500 Traefik, 49501 dashboard, 49502 Consul HTTP, 49503 admin, 49504 HTTPS,
  49506 server, 49507 LAN gossip, 49508 WAN gossip. Dashboard image `lvhy-win08-dashboard:acceptance`, a tag of
  the ticket 03 image `3ae33ee7e814`. No dashboard code changed.
- Linux Workstation `linux-win-peer`, project `lvhy-win-peer`, computer `192.168.122.1`, ports 48380 Traefik,
  48780 dashboard, 48500 Consul HTTP, 48300 server, 48302 WAN gossip. Published dashboard image.
- Linux native target `192.168.122.1:48913` returns `linux-native-yard-target`.
- Raw output is in `~/.local/state/lvh-yard-windows-11/evidence/08-*.out`. The authoritative files are named
  below. `08-p2e-owner.out` and `08-p2f-isolation.out` hold two broken first attempts of the container listing
  (a quoting error in my harness); the ACL part of `p2e` is valid, and `p2g` and `p2h` replace the rest.

## Phase 1: Windows holds the Yard CA and sends; Linux receives

Files `08-p1a.out` to `08-p1d.out`, `08-p1-rpc-probe.out`, `08-p1-requests.out`.

1. Windows (`ws init --address 192.168.122.117 --gossip-key <peer's> --peers 192.168.122.1:48302`, ports set with
   `ws set`). Then `ws tls ca create`, `ca issue workstation win-yard-08`, `ca issue consul linux-win-peer` and
   `ca install --consul --traefik=false` for itself.
2. **Native Windows refuses.** `ws tls ca install linux-win-peer --consul --traefik=false --scp yarddev@192.168.122.1`
   exited 1 with `--scp: native Windows cannot copy the files with ssh. Nothing was changed.` and the manual
   steps. A name-and-hash listing of the whole Workstation Folder was equal before and after, and
   `certificates\` did not exist. A second try with `--scp 'yarddev@192.168.122.1:C:\x'` gave the same result.
   The VM has `ssh.exe` and `scp.exe`; the CLI did not start either.
3. **Manual save.** The same command without `--scp` made `certificates\linux-win-peer\consul\` with exactly
   `ca.crt` (554 bytes), `consul.crt` (664) and `consul.key` (227). No `ca.key` was in `certificates\`.
   It printed the manual steps, including `Native Windows cannot copy the files for you with --scp`.
4. **Manual transfer.** On Linux, `scp -r` as `yarddev` pulled that folder into a mode 700 folder. The SHA-256
   of the three files matched the Windows files.
5. **Linux imports** with the supported command `lvh-yard ws tls import consul --ca ... --cert ... --key ...`
   (the Yard CA key was never on Linux). Modes afterwards: `tls/` 700, `tls/consul/` 700, the three files and
   `consul.d/10-tls.hcl` 600. `tls/traefik/` stayed empty. The installed hashes equalled the received ones.
6. Linux `ws set peers 192.168.122.117:49508`. Both stacks were recreated with Consul TLS on.
7. **Joined with mutual TLS.** `consul members -wan` on both sides listed `linux-win-peer` and `win-yard-08`
   `alive`. Both agents reported `VerifyIncoming`, `VerifyOutgoing` and `VerifyServerHostname` true. Strict
   Windows status and Linux status were `ok`, each with two Workstations in the WAN pool.
8. **Real handshakes** from Linux to both RPC ports (`08-p1-rpc-probe.out`), TLS 1.3:

| Client                              | Windows `:49506`                    | Linux `:48300`                      |
| ----------------------------------- | ----------------------------------- | ----------------------------------- |
| Certificate signed by the Yard CA   | handshake OK, connection stays open | handshake OK, connection stays open |
| No client certificate               | `TLSV13_ALERT_CERTIFICATE_REQUIRED` | `TLSV13_ALERT_CERTIFICATE_REQUIRED` |
| Certificate from another (rogue) CA | `TLSV1_ALERT_UNKNOWN_CA`            | `TLSV1_ALERT_UNKNOWN_CA`            |
| Plain TCP, no TLS                   | closed by the server                | closed by the server                |

9. **Yard Publications, both ways** (`08-p1-requests.out`). A Python server in a host-network container on
   Windows `0.0.0.0:49550` returns `windows-yard-target-08`. Windows registered it as a Static Instance
   (`winweb08a`, host defaulted to `192.168.122.117`) and published it with Yard Scope.
   Linux `catalog` listed `winweb08a.win-yard-08.lvh.me`. Linux Consul health for it had `serfHealth` and the
   service check `passing`. A request through the Linux Traefik with Host `winweb08a.win-yard-08.lvh.me` on
   `127.0.0.1:48380` returned HTTP 200 and `windows-yard-target-08`.
   Linux registered its native target `192.168.122.1:48913` (`linuxtarget08a`) and published it. Windows `catalog`
   listed `linuxtarget08a.linux-win-peer.lvh.me`, Windows Consul health for it was `passing`, and a request
   through the Windows Traefik with that Host on `127.0.0.1:49500` returned HTTP 200 and
   `linux-native-yard-target`. (The Windows request in the first script run failed on a quoting mistake of my
   harness. The command was repeated once and succeeded; the output file shows both.)

## Phase 2: Linux holds a new Yard CA and sends; Windows receives

Files `08-p2a.out` to `08-p2d.out`, `08-p2-requests.out`, `08-p2e` to `08-p2k`.

1. On Linux, `ws tls ca create` made a new Yard CA, `ca issue workstation` signed for both Workstations, and
   `ca install --consul --traefik=false` installed Linux's own certificate. The CA replaces the phase 1 CA.
2. **Unix sender, fake Windows transport.** A fake `ssh` and `scp` on `PATH` record every command. The fake
   `ssh` answers `uname -s` as `cmd.exe` does (`'uname' is not recognized`, exit 1). The real Linux CLI ran
   `ws tls ca install win-yard-08 --consul --traefik=false --scp me@win.lan`. Exit 1, `--scp: me@win.lan does not look
like a Unix computer, but like Windows ... Nothing was changed on either computer.` The recorded commands
   were one line: `ssh me@win.lan uname -s`. No `umask`, `mkdir` or `scp` ran. A name-and-hash listing of the Linux
   Workstation Folder (without `consul-data` and Traefik's dynamic files, which change on their own) was equal
   before and after, and `certificates/` did not exist. With a drive-letter folder
   (`me@win.lan:C:\Users\me\yard`) the CLI refused with no command at all (zero recorded).
3. **Unix-to-Unix unchanged, fake Unix transport.** The fake `ssh` answered `uname -s` with `Linux`. The recorded
   commands were, in order: `ssh me@unix.lan uname -s`, `ssh me@unix.lan umask 077 && mkdir -p '/srv/yard/tls'`,
   `scp -r .../certificates/other-unix/consul me@unix.lan:/srv/yard/tls/`. The CLI printed `Copied the folders to ...`.
   The only new step against the old behavior is the read-only probe first.
4. **Real CLI against the real Windows VM.** The CLI ran `ws tls ca install win-yard-08 --consul --traefik=false --scp
yarddev@192.168.122.117` with a real `ssh` (a wrapper that adds the helper's password options and records the
   call) and an `scp` wrapper that would exit 99 if it were ever run. Exit 1, the same refusal as above. The
   recorded transport was a single line: `ssh yarddev@192.168.122.117 uname -s`. By hand, that probe answers
   `'uname' is not recognized as an internal or external command` with exit 1. Comparison of the Windows account
   before and after: the names in the home folder, the names in `.config`, the absence of `.config\lvh-yard`, and
   the SHA-256 of every file under `lvh-dev\08` were equal; the Linux folder listing was equal too
   (`08-p2b-real.out`). The VM had no `uname`.
5. **Manual save and transfer.** `ca install win-yard-08` without `--scp` made `certificates/win-yard-08/` with
   `consul/{ca.crt,consul.crt,consul.key}` and `traefik/{ca.crt,traefik.crt,traefik.key}`, and nothing else. No
   `ca.key` was in it. The sender then pushed the two folders with `scp -r` as `yarddev` to
   `C:\Users\yarddev\lvh-dev\08\received\` (outside the Workstation Folder). The hashes matched.
6. **Windows imports** `ws set https-port 49504`, then `ws tls import consul` and `ws tls import traefik`, each with
   `--ca`, `--cert` and `--key` from `received\`. The files in `tls\consul` and `tls\traefik` had the received
   hashes; `consul.d\10-tls.hcl` and `traefik\dynamic\10-tls.yaml` appeared.
7. Both stacks were recreated. **Mutual TLS again**: both `consul members -wan` lists showed both Workstations
   `alive`; both agents showed the three verify flags true; strict Windows status `ok`; Linux status `ok`.
   The probe table for the new CA gave the same four results as in phase 1 for both ports. As a control, the phase
   1 certificate (signed by the replaced CA) was refused by both servers with `TLSV1_ALERT_UNKNOWN_CA`.
8. **Requests again, new Services** (`08-p2-requests.out`): `winweb08b` through the Linux Traefik returned HTTP 200
   and `windows-yard-target-08`; `linuxtarget08b` through the Windows Traefik
   returned HTTP 200 and `linux-native-yard-target`. Health checks were `passing` on the remote side.
   The Services `winweb08a` and `linuxtarget08a` from phase 1 stayed healthy across the CA change.
9. **HTTPS with the manually imported Traefik role.** On Windows, `curl.exe --ssl-no-revoke --resolve
winweb08b.lvh.me:49504:127.0.0.1 --cacert received\traefik\ca.crt https://winweb08b.lvh.me:49504/` returned
   `windows-yard-target-08 HTTP=200`. Without `--cacert`, `curl.exe` failed with exit 35.
10. **Terminal check (ConPTY).** `lvh-yard.exe ws tls ca install` on a real Windows console (`win-tty`, driven by
    `pexpect`) asked for the Workstation name, then asked about Consul. It did **not** ask the scp question, and
    its output contained `Native Windows cannot copy` and `If linux-win-peer runs on Windows`, and no offer of
    `--scp` (`08-p2k-conpty.out`).

## Private keys and role isolation after manual import

- **Windows ACLs** (`08-p2e-owner.out`, `Get-Acl`): `tls\consul\consul.key`, `tls\traefik\traefik.key`, `ca\ca.key`,
  `.env` and `certificates\linux-win-peer\consul\consul.key` allow only SYSTEM, Administrators and `yarddev`, and the
  access rules are protected. No file had an entry for `Users`, `Everyone` or `Authenticated Users`.
  The received folder `received\` is not changed by the CLI. Its files had inherited ACLs from the user profile
  (`protected=False`, the same three accounts). The guide tells the recipient to keep it in a private folder
  and delete it.
- **Second user** (`yardother`, non-administrator, `08-p2i-other-user.out`): a world-readable control file was read
  (92 bytes). Reads of eight files (both received keys, both installed keys, an installed `ca.crt`, `ca\ca.key`, the
  old bundle key, `.env`) all gave `UnauthorizedAccessException`, as did listings of `received`, `tls\consul` and
  `certificates`.
- **The Linux CA key never reached Windows.** No file under `lvh-dev\08` had the SHA-256 of the Linux `ca.key`, and no
  file named `ca.key` was in `received\` or `tls\`.
- **Containers** (`08-p2h-isolation.out`, `08-p2g-isolation.out`). Consul (uid 1000) sees only `/consul/tls/ca.crt`
  and its own `consul.key`. Traefik (uid 0) sees `ca.crt` and its own `traefik.key` in `/source/tls` and in its staged
  copies, and no other key. The dashboard sees no key or CA file. Traefik could not read the Consul key or
  a `ca.key` at six paths, including `../consul/consul.key`. Consul could not read the Traefik key or a `ca.key` at
  five paths. Each could read its own key. The old Windows `ca.key` (a hash that I knew) was visible in neither.
  Mounts are the same as in ticket 07: no container mounts `ca\`.
- **Linux** (`08-p2j-linux.out`): `tls/` 700, `tls/consul/` 700, imported and installed files 600, `ca/ca.key` 600,
  `certificates/` 700 and its key 600. The Consul container sees only `/consul/tls/{ca.crt,consul.crt,consul.key}`, and
  no `ca.key` exists in the Consul, Traefik or dashboard container.
- A whole private Workstation Folder was never copied. The manual handoff moved three files per role.

## Native tests

`go test -c` for `./internal/command` was cross-built and run on the VM as `yarddev`
(`08-native-tests.out`): the new tests and the existing `WSTLSInstall`, `WSTLSImport` and `WSTLSEnable` tests
passed natively (`PASS`, exit 0). One existing test skipped (`TestWSTLSInstallRefusesEscapingRoleDirectory`, a
symlink test that skips on Windows).

## Defects found

Both appeared as failing component tests before the fix. They were not observed on a real system.

1. **No native Windows rejection.** `ws tls ca install <name> --scp user@host` on a Windows CLI would write the
   bundle and then run `ssh <host> "umask 077 && mkdir -p ..."` and `scp -r`. The terminal also asked the scp
   question. Fixed: `checkNativeWindowsSender` refuses before anything is written, and the question is not asked.
2. **No Unix-sender check of the destination.** The sender wrote its bundle first, validated the target at the end,
   and ran the Unix `umask`/`mkdir` command on the destination without checking what it was. Fixed:
   `checkHandoffDestination` validates the target, refuses Windows-looking folders, and runs the read-only
   `ssh <host> uname -s` probe, all before any write. The probe tells a failed login (exit 255, reported as an ssh
   failure) from a destination that is not Unix.

## Limits

- The Windows destination was tested over its real `ssh`, whose default shell is `cmd.exe`. The VM has no
  `uname`. A Windows computer with Git for Windows `uname`, or with a changed default shell, answers differently:
  the CLI refuses names containing `MINGW`, `MSYS`, `CYGWIN` or `WINDOWS` (component test with a fake answer). It
  was not run against such a computer.
- Only the Consul and Traefik certificates were handed over. Client Certificates (`.p12`) are not part of
  Workstation membership and were not part of this handoff.
- PowerShell 7 and the `yardother` runtime were not used for the Yard join itself. The join ran in Windows
  PowerShell 5.1 as `yarddev`.
- One early `consul members -wan` on Linux ran without `-http-addr`. In a host-network container it reached the
  host's own Consul on `127.0.0.1:8500`, which listed other machines. I only read and changed nothing there. Every
  later call named the peer's port.
- After the runs, the Windows stack was stopped (not removed). The Linux peer was restored: Consul TLS off, the CA
  and the certificates removed, my two Services deleted, `win-yard-08` pruned from its WAN pool, `YARD_PEERS` back to
  its earlier value `192.168.122.117:49388`. Its status is `warning` only because that Peer does not answer, as it
  did before.

## Tests in the repository

- `ws_tls_handoff_test.go` (new): native Windows refuses `--scp` before any change (name given as argument and by
  prompt); no scp question on Windows and the manual steps are printed; the bundle never holds `ca.key`; a Unix
  sender refuses a Windows destination (cmd.exe answer and an MSYS `uname`) after one read-only probe; Windows
  folder forms need no remote command; a failed ssh login is not called Windows; the Unix-to-Unix command order;
  invalid targets leave the folder as it was; the invitation names the manual way.
- `ws_tls_import_test.go`: the Unix-to-Unix expectations now include the probe.
- `mise run test:unit` and `mise run check` passed on this branch.
