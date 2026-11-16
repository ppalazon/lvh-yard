# Private local Windows Workstation evidence

The ordinary-user generated Workstation passed direct Compose startup, private
file access checks, native loopback routing, recreation and configuration reload.
The controlled second-computer listener isolation check also passed.
The approved IPv4-only runtime also passed explicit IPv6 rejection and
unsupported-target Reachability checks. These results complete this setup gate;
the remaining Windows support gates have their own acceptance scope.

## Environment and sources

Observed on 2026-11-14:

- Windows 11 Pro x64, build 26300, on VM `192.168.122.117`.
- Windows PowerShell 5.1.26100.9818 and portable PowerShell 7.7.0.
- Docker Desktop 4.99.0, Linux engine 29.9.3, Compose 5.7.0,
  context `desktop-linux`, with host networking enabled.
- Runtime account `t-x\yarddev`, SID ending `1002`. Its token lists
  `BUILTIN\Users` and Medium Mandatory Level, with no administrator group.
- Privacy account `t-x\yardother`, SID ending `1003`, also an ordinary user.
- Worktree `.claude/worktrees/win11-03`.

The environment version sources are
[the native CLI evidence](01-native-windows-cli-workflows.md#environment-and-scope)
and `/home/ppalazon/.local/state/lvh-yard-windows-11/notes/README.md`.
The runtime and privacy captures below record the actual account tokens.
The maintainer's host-networking confirmation is in `history://WorkstationResume`.

All `bin/` and `evidence/` source paths below are relative to:

```text
/home/ppalazon/.local/state/lvh-yard-windows-11/
```

The raw output is local to the measurement host. The commands and selected
results here preserve the measured claims. Implementation descriptions cite
repository files separately; they are not additional runtime assertions.

| Scope                                                                | Harness                                                      | Raw output                                                                          |
| -------------------------------------------------------------------- | ------------------------------------------------------------ | ----------------------------------------------------------------------------------- |
| Direct Compose, identities, socket, Backup, native Route, recreation | `bin/test-workstation-03.ps1`                                | `evidence/03-final-runtime.out`                                                     |
| Actual second-user reads                                             | `bin/test-workstation-03-private.ps1`                        | `evidence/03-second-user-privacy.out`                                               |
| User-owned files and direct Compose bind rejection                   | `bin/test-workstation-03-owned.ps1`                          | `evidence/03-owned-and-direct-bind.out`                                             |
| Production configuration and certificate reload                      | `bin/test-workstation-03-reload.ps1`, `bin/03-tls-client.py` | `evidence/03-production-reload.out`                                                 |
| Native adapter selection and console prompt                          | native CLI and ConPTY                                        | `evidence/03-native-lan-selection.out`, `evidence/03-interactive-lan-corrected.out` |
| IPv4 wildcard control and LAN isolation                              | Linux socket client                                          | `evidence/03-lan-isolation-ipv4-control.out`                                        |
| Staging shell and Traefik shutdown                                   | direct Compose stop/start                                    | `evidence/03-traefik-shutdown.out`                                                  |
| IPv4-only target enforcement                                         | `bin/test-workstation-03-ipv4-policy.ps1`                    | `evidence/03-final-ipv4-policy.out`                                                 |

The runtime harnesses ran through these ordinary-user commands. The launcher
copies a UTF-8 BOM script and invokes Windows PowerShell 5.1 without a profile.

```sh
state=/home/ppalazon/.local/state/lvh-yard-windows-11
set -o pipefail
WIN_USER=yarddev "$state/bin/win-ps" "$state/bin/test-workstation-03.ps1" 2>&1 | tee "$state/evidence/03-core-runtime-complete.out"
WIN_USER=yardother "$state/bin/win-ps" "$state/bin/test-workstation-03-private.ps1" 2>&1 | tee "$state/evidence/03-second-user-privacy.out"
WIN_USER=yarddev "$state/bin/win-ps" "$state/bin/test-workstation-03-owned.ps1" 2>&1 | tee "$state/evidence/03-owned-and-direct-bind.out"
WIN_USER=yarddev "$state/bin/win-ps" "$state/bin/test-workstation-03-reload.ps1" 2>&1 | tee "$state/evidence/03-production-reload.out"
```

## Generated standalone runtime

The CLI generated the Workstation in a path containing spaces, an apostrophe
and Unicode. Direct Docker Compose used those files without a CLI overlay or
hidden preparation step:

```powershell
$root = Join-Path $env:USERPROFILE 'lvh-dev\03'
$folder = Join-Path $root "O'Brien café 日本\workstation"
$cli = Join-Path $root 'lvh-yard.exe'
& $cli ws init --dir $folder --workstation win-native-03 --address 192.168.122.117
# The harness sets the isolated ports listed under Retained fixture.
docker.exe compose --project-directory $folder -p lvhy-win-03 config --quiet
docker.exe compose --project-directory $folder -p lvhy-win-03 up -d --wait --wait-timeout 180
```

`03-core-runtime-ipv4.out:37-127` records generation and fresh startup.
`03-core-runtime-complete.out:37-73` records the completed run's healthy Consul,
dashboard and Traefik, plus the successful one-shot volume initializer.
The initial dashboard image was built from this worktree with
`docker build -t lvhy-win03-dashboard:acceptance .`, saved and loaded into
`yarddev`'s engine. Its initial build ID was
`sha256:0c0fbdf25e388d481fff9fe323a2853b21f889a7b1845d185537bb165c308629`
(`artifact://349`).

After the approved IPv4 policy changes, the final build and load produced:

| Artifact                             | SHA256                                                             |
| ------------------------------------ | ------------------------------------------------------------------ |
| Linux build image ID                 | `7bb176ba0c17777b0bfe3044efad25593dc72eb85d51d19e5defa50205516c26` |
| Windows loaded and running image ID  | `3ae33ee7e814690f37deb5d0abb5d57ede3b66193318add51bcd563f99f11b84` |
| `/app/dashboard.jar` on both engines | `f7a5962c5dd22345800123a2765f50af8479c463289ac39d1b1eac1c3c95f00d` |

The two engines reported identical filesystem layer digests despite their
different image identifiers. Source: `03-final-image-identity.out`.
`03-final-regeneration.out` records the final generated Compose setup with
`YARD_IPV4_ONLY=true`; it retained the fixture's user-owned ports and image
override. `03-final-runtime.out` repeats direct startup, native Health,
Reachability, the exact Route response, Backup writing and recreation with
that final image. No launch-time overlay or process-environment repair was used.

The core harness ran `id`, a real Docker socket `_ping`, and
`test -w /consul/data` inside the running containers. It observed:

```text
uid=1000 gid=1000(consul) groups=1000(consul)
uid=1000(ubuntu) gid=1000(ubuntu) groups=1000(ubuntu),0(root)
OK1000
drwxr-xr-x ... 1000 consul ... /consul/data
```

`OK1000` joins the socket's `OK` response and the next command's UID output.
Consul wrote its state in the project-owned named volume
`lvhy-win-03_consul-data`. The dashboard ran as UID 1000 with supplementary
group 0 and reached the socket API. Source: core harness lines 37-48 and
`03-core-runtime-complete.out:62-73,234-236`.

## Private Backups and role mounts

The dashboard's actual `POST /backups/new` returned HTTP 303. The resulting
`backups\win-native-03-20261114-130639.yaml` was 1449 bytes, and the harness
read its YAML and asserted that it contained `native03`.
The CLI also exported `backups\cli-private.yaml`. A real `ws tls ca create`
created the CA key used in the denial check.
Source: core harness lines 97-106 and `03-core-runtime-complete.out:218-233`.

From a separate `yardother` session, the privacy harness first read
`C:\Users\yarddev\lvh-dev\03\readable-control.txt` and checked its exact content.
It then attempted real `Get-Content -Raw -ErrorAction Stop` reads:

```text
PUBLIC CONTROL READ PASSED
READ DENIED: .env
READ DENIED: ca\ca.key
READ DENIED: backups\cli-private.yaml
ACTUAL SECOND-USER SECRET AND BACKUP READ DENIAL PASSED
```

The harness accepts only `System.UnauthorizedAccessException` as denial.
This is an actual second-account access check, beyond an ACL listing.
Source: `bin/test-workstation-03-private.ps1:6-14` and
`evidence/03-second-user-privacy.out:1-26`.

The container mount inspection recorded Consul's read-only `tls/consul` mount
and Traefik's read-only `tls/traefik` mount. Neither container received the CA
folder or the other role's TLS folder. Traefik also received read-only dynamic
configuration and its generated shell script. Source:
`03-core-runtime-complete.out:234-236` and `config/compose.windows.yaml:49-52,92-95`.

## Native loopback Route and persistence

A native Windows PowerShell job served the exact body
`native-windows-loopback-03` on `127.0.0.1:49185`. The CLI registered an HTTP
check, then published the Service with Local Scope:

```powershell
$dashboard = 'http://127.0.0.1:49181'
& $cli --dashboard $dashboard service create native03
& $cli --dashboard $dashboard register static native03 --host 127.0.0.1 --port 49185 --check http --path / --interval 1s --timeout 1s
& $cli --dashboard $dashboard publish native03 --scope local
& $cli --dashboard $dashboard test native03
curl.exe --noproxy '*' -fsS -H 'Host: native03.lvh.me' http://127.0.0.1:49180/
```

Consul reported `passing` and
`HTTP GET http://127.0.0.1:49185/: 200 OK Output: native-windows-loopback-03`.
Reachability reported the Instance and Route as `ok`, with `Every check passed`.
The separate HTTP request returned the exact native marker.
The initial publish message said `unhealthy`; these passing observations came
after the check had run. Source: core harness lines 57-91 and
`03-core-runtime-complete.out:96-214`.

A registration without `--host` used `192.168.122.117:49185`, preserving the
native LAN identity. It remained Unpublished and became critical because this
fixture listened only on loopback. The completed run also showed Consul's
leader at the native LAN address. `consul members` separately reported
`win-native-03 192.168.122.117:49187 alive`.
Sources: `03-core-runtime-complete.out:84-95,215-217,270-290` and
`03-owned-and-direct-bind.out:18-20`.

The native adapter inventory listed Ethernet at `192.168.122.117`, loopback,
and the WSL virtual adapter at `172.19.144.1`. Init without `--address` selected
Ethernet and saved `YARD_BIND_ADDRESS=192.168.122.117`.
An ordinary-user ConPTY session also offered the LAN IPv4 address.
Accepting the suggestion wrote the setup and returned zero.
Sources: `03-native-lan-selection.out` and `03-interactive-lan-corrected.out`.

Direct Compose `up -d --force-recreate --wait --wait-timeout 180` recreated the
runtime. The catalog retained `native03` and `default03`; the native Route still
returned its exact marker. The harness asserted that response before printing
its success line. The catalog also showed `traefik-admin` and `yard` as
unhealthy; this run does not claim every catalog Service was healthy.
Source: core harness lines 112-117 and `03-core-runtime-complete.out:239-292`.

## Measured Java transport failure and IPv4 scope

The initial dashboard could not reach Consul or Traefik through Java, although
all containers were healthy and Docker socket access passed. Strict status
reported a Consul `I/O error` and Traefik `ConnectException`.
From the same dashboard container, curl reached the exact Consul leader URL
and Traefik ping URL with HTTP 200. All three containers used host networking.
Sources: `03-core-runtime-netstat.out:78-93` and
`03-runtime-network-diagnostic.out:1-67`.

A focused Java probe tested `Socket` and JDK `HttpClient` against
`127.0.0.1:49182`. The default JVM printed `preferIPv4Stack=null`, then
`java.net.ConnectException: Connection refused (connect failed)` for the
socket and a `ConnectException` caused by `ClosedChannelException` for HTTP.
Quoting the property correctly in PowerShell produced:

```powershell
docker.exe exec $dash java -cp /tmp LvhyNetworkProbe
docker.exe exec $dash java '-Djava.net.preferIPv4Stack=true' -cp /tmp LvhyNetworkProbe
```

```text
preferIPv4Stack=true
Socket connected /127.0.0.1:57848
HTTP 200 "192.168.122.117:49186"
```

Sources: `03-java-ipv4-probe.out`, `03-java-ipv4-corrected.out`, and the exact
probe commands in `history://WorkstationResume`. The first unquoted property
attempt failed with `Could not find or load main class .net.preferIPv4Stack=true`;
it was a command-parsing failure, not the successful comparison.

The same-image, UID-1000 comparison against a real Java IPv6 listener on
`[::1]:49192` measured the cost. The unchanged JVM returned `IPv6 response=42`.
With the property, the client failed with
`java.net.SocketException: IPv6 protocol family unavailable`.
Both clients used `--network host`, `--entrypoint java` and
`-cp /probe LvhyIPv6Probe client`; only the second added the quoted property.
The temporary server container `lvhy-win03-ipv6` was removed.
Source: `03-java-ipv6-impact.out` and `history://WorkstationResume`.

The maintainer explicitly approved IPv4-only Windows runtime support, including
handling IPv6 targets as unsupported. Linux and macOS defaults remain outside
that exception. The Windows-only generated Java command uses the measured
property (`config/compose.windows.yaml`). The approval is recorded in the
maintainer messages in `history://WorkstationResume` and the specification.

The final native runtime returned HTTP 400 with a host error when asked to
register `::1`. Its detail said `Windows runtime supports IPv4 targets only`.
A real IPv6 listener then gave a foreign Consul TCP check passing Health.
The dashboard still reported `reached: false`, `passed: false` and the explicit
unsupported-target explanation, with no Route. An actual request through
Traefik returned HTTP 404. The harness removed its temporary IPv6 container,
registration and Services. Source: `03-final-ipv4-policy.out`.

## Production configuration and certificate reload

The reload harness used the running generated Compose Traefik, not the earlier
standalone prototype. A matching-SNI TLS client requested `stage03.lvh.me` on
port 49184 and hashed the certificate's actual DER bytes.
Both certificate observations returned HTTP 200:

| Observation                                           | Served certificate DER SHA256                                      | HTTP |
| ----------------------------------------------------- | ------------------------------------------------------------------ | ---- |
| Original certificate and key                          | `83acb060b51df6c257c082533b7465fe1b856d1ae6dad299e5538d3fc1fc3529` | 200  |
| Certificate and key replaced at the same source paths | `9a008d9cf12d7541fd22b3d113ea08a83859a37bebb72b7d7f3b263a5058675d` | 200  |

The source `traefik\dynamic\90-stage.yaml` SHA256 remained
`95D38197F4400037B595C3ADE9DDCE4B577F5186618E353441242642EE902C46`.
No YAML rewrite triggered the certificate-only change.
An arbitrary `91-stage.toml` first served `before03.lvh.me` with HTTP 200.
After its rule changed, the old host returned 404 and `after03.lvh.me` returned 200. Deleting the configuration returned 404. The harness also asserted that
the deleted YAML, TOML, certificate and key were absent from the Linux runtime.
Sources: reload harness lines 15-74, TLS client lines 6-19, and
`03-production-reload.out:1-70`.

The generated runtime uses a shell script already mounted into Traefik.
It adds no helper binary, configuration renderer or sidecar. Its code copies
the complete source dynamic and TLS trees twice, one second apart, and accepts
only matching snapshots. It atomically switches the Linux TLS directory
symlink before publishing configuration or touching unchanged local files.
Changed dynamic files use same-filesystem rename; deleted files disappear.
Private staging uses `umask 077`, and published configuration uses mode 600.
These are implementation observations from
`config/traefik-windows.sh:3-4,28-65,67-73,85-107`, bounded by the measured
reload cases above. They do not establish behavior for every concurrent writer.

Direct Compose stopped Traefik with a five-second limit.
The shell and child exited with code 143, without SIGKILL or OOM termination.
Direct Compose then restarted the same service successfully.
Source: `03-traefik-shutdown.out`.

## Owned files and listener limits

The upgrade harness added a user-owned Compose comment and a user-owned TOML
file, then ran `lvh-yard ws compose upgrade --dir $folder`.
It asserted unchanged Compose and `.env` hashes and exact TOML content.
Direct Compose also rejected a native interface bind for each service:

```powershell
docker.exe compose --project-directory $folder -p lvhy-win-03 run --rm --no-deps -e YARD_DASHBOARD_BIND_ADDRESS=192.168.122.117 dashboard
docker.exe compose --project-directory $folder -p lvhy-win-03 run --rm --no-deps -e YARD_TRAEFIK_BIND_ADDRESS=192.168.122.117 traefik
```

Each exited nonzero and printed
`Windows listeners require 127.0.0.1 or 0.0.0.0.`
Sources: owned harness lines 5-21 and `03-owned-and-direct-bind.out:1-16`.

Local TCP connections to the four default HTTP listeners succeeded.
`netstat -ano` showed `127.0.0.1` listeners on ports 49180-49183.
This is local bind evidence only. The Linux second-computer probe first tried
its wildcard positive control:

```python
with socket.create_connection(('192.168.122.117', 49191), timeout=5) as s:
    s.sendall(b'GET / HTTP/1.1\r\nHost: control\r\nConnection: close\r\n\r\n')
```

The initial whoami wildcard connection failed with `TimeoutError: timed out`
before the probe reached its four isolation assertions.
`03-lan-listener-isolation.out` is empty because `tee` captured stdout only;
the traceback is in the `bg_84` result in `history://WorkstationResume`.
That failed control does not prove isolation.

A distinct scenario replaced the control with Python's IPv4 listener:

```powershell
docker.exe run -d --name lvhy-win03-wildcard --network host lvh-probe:1 python3 -m http.server 49191 --bind 0.0.0.0
```

The Linux computer reached `192.168.122.117:49191` and received HTTP 200.
The same client then timed out on all four default loopback listeners:
Traefik HTTP 49180, dashboard 49181, Consul HTTP 49182 and Traefik admin 49183.
This reachable IPv4 wildcard control establishes the tested listener isolation.
No firewall rule changed for the test.
Sources: `03-ipv4-wildcard-control.out` and `03-lan-isolation-ipv4-control.out`.

## Check coverage and failed attempts

All six native Workstation command tests passed under Windows PowerShell 5.1
and PowerShell 7. The command was
`workstation.test.exe '-test.run=^TestWindowsWorkstation' '-test.v'`.
The PowerShell 7 launcher used
`WIN_PS='C:\Users\yarddev\lvh-dev\01\pwsh7\pwsh.exe'`.
Sources: `03-final-runtime.out` and `03-powershell7-six-tests.out`.
The tests cover default executable/folder separation, explicit Unicode paths,
missing address and override, invalid filenames, junction escapes, and
shared or synced folder rejection.

The final `mise run test:unit` and `mise run check` passed, including the
new IPv4 policy component checks and existing Unix regressions.
Source: `artifact://405` and the completed check command in
`history://WorkstationResume`. Windows cross-compilation and
`GOOS=windows GOARCH=amd64 go vet ./...` also passed.
The required commit hooks passed after formatting, including the secret scan.

Earlier failures remain recorded:

- `03-native-command.out` is empty. `03-native-command-corrected.out` records
  real `Access is denied` failures while protecting native test folders.
- `03-core-runtime.out` stopped because PowerShell resolved the harness name
  `Cli` to `Clear-Item`. `03-core-runtime-corrected.out` stopped at
  `Get-NetTCPConnection`: `Cannot connect to CIM server. Access denied`.
  The completed harness uses `Invoke-Yard03` and `netstat.exe`.
- `03-core-runtime-ipv4.out:275-285` records a passing Instance but a Route
  timeout within 5 seconds. The completed harness handles an empty accepted
  connection and records both checks passing. The earlier failed result stays
  part of the evidence.

## Retained fixture

The harness retained project `lvhy-win-03` and folder
`C:\Users\yarddev\lvh-dev\03\O'Brien café 日本\workstation`.
The native job on port 49185 stopped in `finally`; the passing Route result
applies while that job was running. The success output warns that `native03`
then loses Health. The IPv4 wildcard control container `lvhy-win03-wildcard`
remains available for later controlled LAN measurements.

| Port  | Fixture use                               |
| ----- | ----------------------------------------- |
| 49180 | Traefik HTTP                              |
| 49181 | Dashboard HTTP                            |
| 49182 | Consul HTTP                               |
| 49183 | Traefik administration and ping           |
| 49184 | Traefik TLS reload probe                  |
| 49185 | Native Windows loopback HTTP job, stopped |
| 49186 | Consul server                             |
| 49187 | Consul LAN                                |
| 49188 | Consul WAN                                |
| 49189 | Consul DNS                                |
| 49190 | Consul gRPC TLS                           |
| 49191 | Wildcard positive control                 |

The reload harness removed its stage files, and the ownership harness removed
its temporary TOML file. Other Workstation fixtures and resources remain outside
this evidence's scope. No Docker Desktop restart, federation or Unpublished
Bound Instance behavior is claimed here.
