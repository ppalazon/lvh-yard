# Mixed-platform Yard evidence

A real Windows 11 Workstation and a real Linux Workstation on the same LAN joined each other in both
directions, and each routed a Service that the other published. The first attempt found a real defect:
Consul's bind to the Docker VM address was unreachable from the LAN. The generated Windows Compose now binds
Consul to `0.0.0.0` and advertises the LAN address. The remaining observations below are from the fixed setup.

The shared VM was overloaded during this run (see [Load and stalls](#load-and-stalls)). Cross-Workstation
reads stalled for seconds. Every Route proof below succeeded, some only after retries. The stalls are not
diagnosed.

## Environment

Observed on 2026-11-14:

- Windows 11 Pro x64 VM `192.168.122.117`, ordinary account `t-x\yarddev` (see the ticket 03 evidence for its
  token). Docker Desktop 4.99.0, Linux engine 29.9.3, host networking on. The VM has 2 CPUs and 3.9 GB.
- Windows CLI cross-compiled from this branch (`GOOS=windows GOARCH=amd64 CGO_ENABLED=0`), run in Windows
  PowerShell 5.1 through `win-ps`.
- Dashboard image `lvhy-win05-dashboard:acceptance`, a second tag of the ticket 03 image
  (`sha256:3ae33ee7e814690f37deb5d0abb5d57ede3b66193318add51bcd563f99f11b84`). No dashboard code changed.
- Windows Workstation `win-yard-05`, project `lvhy-win-05`, folder `C:\Users\yarddev\lvh-dev\05\workstation`,
  address `192.168.122.117`, ports 49380 Traefik, 49381 dashboard, 49382 Consul HTTP, 49383 Traefik admin,
  49386 server, 49387 LAN gossip, 49388 WAN gossip, 49389 DNS, 49390 gRPC TLS.
- Linux peer `linux-win-peer`, project `lvhy-win-peer`, computer `192.168.122.1` (`virbr0`), CLI built from the
  integration branch. Its ports are 48380 Traefik, 48780 dashboard, 48500 Consul HTTP, 48300 server, 48301 LAN
  gossip, 48302 WAN gossip. The peer uses the published dashboard image `ghcr.io/ppalazon/lvh-yard-dashboard:latest`.
- Linux native target `192.168.122.1:48913` returns `linux-native-yard-target`.

Raw output lives in `/home/ppalazon/.local/state/lvh-yard-windows-11/evidence/05-*.out`. Harness scripts are
`bin/test-workstation-05-*.{ps1,sh}`, `bin/win05-run` and `bin/05-lan-probe.py` in the same folder. The Gossip
Key is read from the peer's `.env` by `bin/win05-run`, which masks it in output. It was not recorded here.
One `ps` listing inside the peer's Consul container showed the key once in a tool result while I looked for
retry-join arguments. Treat that fixture key as seen; the fixture is isolated.

## Defect found: Consul unreachable from the LAN

The generated Windows Compose bound Consul with `-bind='{{ GetDefaultInterfaces ... }}'`. With the Windows
Workstation started and the Linux peer given `YARD_PEERS=192.168.122.117:49388`, the Linux peer never joined.
Observations (`05-phase-a-linux-joins-windows.out`, `05-bind-wildcard-experiment.out`):

- From Linux, TCP connects to `192.168.122.117` ports 49386, 49387, 49388 and 49382 all failed.
- Inside the Windows Consul container, Consul listened on `192.168.65.3:49386-49388` (the Docker VM address).
  `/v1/agent/self` showed `BindAddr 192.168.65.3`.
- Windows `netstat -ano` listed no listener for 49386-49388 on `192.168.122.117`. Only loopback and wildcard
  listeners were forwarded. `192.168.122.117:50499 -> 192.168.122.117:49386 SYN_SENT` showed Consul's own
  connection to its advertised address failing.

After the change to `-bind=0.0.0.0` (still `-advertise` and `-advertise-wan` set to the LAN address):

- `netstat` showed `0.0.0.0:49386-49388` TCP and `0.0.0.0:49387-49388` UDP listeners owned by the Docker backend.
- Linux TCP connects to 49386, 49387 and 49388 succeeded, and the Linux peer joined within one retry interval.
- `/v1/agent/self`: `BindAddr 0.0.0.0`, `AdvertiseAddrLAN 192.168.122.117`, `AdvertiseAddrWAN 192.168.122.117`,
  member address `192.168.122.117`.
- Consul HTTP (127.0.0.1) stayed loopback only.

The change is in `config/compose.windows.yaml` and its parity copy in `cli/internal/templates/`.

## Join in both directions

Both runs used the real CLI and direct Compose on a fresh Windows Consul volume.

**Linux joins Windows** (`05-phase-a-before-join.out`, `05-phase-a-after-join.out`):

1. Windows started with no Peers. `consul members -wan` on Windows listed only `win-yard-05`
   (`192.168.122.117:49388`). The Linux peer listed only itself.
2. `lvh-yard ws set peers 192.168.122.117:49388` on Linux, then direct Compose `up -d`.
3. Both sides then listed both members `alive`: `linux-win-peer 192.168.122.1:48302` and
   `win-yard-05 192.168.122.117:49388`. Linux `lvh-yard ws yard peers` listed `win-yard-05`. Windows
   `ws yard peers` listed `linux-win-peer`. Windows `status --strict` and Linux `status` reported Consul `ok`
   with two Workstations in the WAN pool.

**Windows joins Linux** (`05-phase-b-reset.out`, `05-phase-b-start.out`, `05-phase-b-after-join.out`):

1. `docker compose down --volumes` removed only project `lvhy-win-05`. On Linux, `consul force-leave -wan -prune`
   removed `win-yard-05`, `ws set peers ""` cleared the setting and the peer's Compose project was brought up
   again. Linux listed only itself.
2. The Windows folder was regenerated from the fixed templates with `ws init --force` (its `.env` kept, with
   `YARD_PEERS=192.168.122.1:48302` added) and started with direct Compose.
3. Both sides listed both members `alive`. Windows `ws yard peers` listed `linux-win-peer 192.168.122.1:48302`.
   Linux `ws yard peers` listed `win-yard-05 192.168.122.117:49388`. Windows `status --strict` returned `ok`.

## Advertised address, bind and Instance host

From `05-adapters.out` and `05-phase-b-after-join.out`:

- `docker exec <dashboard> printenv YARD_INSTANCE_HOST` printed `192.168.122.117`.
- Consul arguments were `-bind=0.0.0.0` and `-advertise`/`-advertise-wan` from `YARD_BIND_ADDRESS`. The agent
  reported `BindAddr 0.0.0.0`, advertise `192.168.122.117`. These are three separate settings.
- A Static Instance registered without `--host` got `192.168.122.117` (`05-routes.out`).
- `lvh-yard ws yard share` printed `Peer address: 192.168.122.117:49388` and
  `Allow TCP port 49386 and TCP and UDP port 49388`.

## Adapter selection and override

`.NET` `NetworkInterface` listed three adapters: `Ethernet 192.168.122.117`, the loopback pseudo-interface, and
`vEthernet (WSL (Hyper-V firewall)) 172.19.144.1`. Results in a non-interactive Windows PowerShell 5.1 session
(`05-adapters.out`):

| Command                                                  | Result                                                                       |
| -------------------------------------------------------- | ---------------------------------------------------------------------------- |
| `ws init` without `--address`                            | exit 0, `YARD_BIND_ADDRESS=192.168.122.117`. The WSL address was not chosen. |
| `--address 192.168.122.117`                              | exit 0, saved.                                                               |
| `--address host.docker.internal`                         | exit 1, `invalid --address`, no files written.                               |
| `--address 127.0.0.1`, `0.0.0.0`, `169.254.10.10`, `::1` | each exit 1, no files written.                                               |
| `--address 172.19.144.1` (the WSL adapter)               | exit 0, saved. It is an explicit choice.                                     |

Limits: the VM has no VPN adapter and I did not add one, so a real ambiguous case with two eligible adapters
was not produced here. The deterministic ambiguity case is the ticket 03 command test
`TestWindowsWorkstationRequiresUnambiguousLANIdentity`. An explicit override is not checked against the adapter
that owns it. The user guide says so.

## Windows Static Instance through the Linux peer's Route

`bin/test-workstation-05-routes.sh`, `05-routes.out`, `05-routes-corrected.out`, `05-final-routes.out`.

A container `lvhy-win05-web` (`lvh-probe:1`, host network, Python `http.server` on `0.0.0.0:49350`) served
`windows-static-yard-target`. From Linux, a direct request to `192.168.122.117:49350` returned HTTP 200 and that
body. On Windows (dashboard `127.0.0.1:49381`):

```powershell
lvh-yard service create winweb05
lvh-yard register static winweb05 --port 49350 --check http --path / --interval 1s --timeout 1s
lvh-yard publish winweb05 --scope yard
```

- Registered address was `192.168.122.117:49350`. Windows `test winweb05` reported the Instance `ok`, the Route
  `answered 200` and `Every check passed`.
- On Linux, `lvh-yard catalog` listed `win-yard-05 winweb05 healthy winweb05.win-yard-05.lvh.me`.
- Linux Consul (`/v1/health/service/winweb05?dc=win-yard-05`) gave address `192.168.122.117`, port 49350, checks
  passing, with the output `HTTP GET http://192.168.122.117:49350/: 200 OK Output: windows-static-yard-target`.
- A request through the Linux peer's Traefik, Host `winweb05.win-yard-05.lvh.me` on `127.0.0.1:48380`, returned
  HTTP 200 and `windows-static-yard-target`. The first attempt used the wrong Host (`winweb05.lvh.me`) and got 404;
  the Yard-scope name is `<service>.<workstation>.<base-domain>`. Host `winweb05.lvh.me` on Linux stays 404.
- The final run (`05-final-routes.out`) succeeded on the third attempt. The first two attempts returned 404 while
  the Linux dashboard held the Windows Workstation as unreachable (see Load and stalls).
- The check was later re-registered with `--interval 5s --timeout 3s` because the 1 s settings flapped to
  `critical` on the loaded VM. The Route proof in `05-final-routes.out` used that registration.

## Linux native target through the Windows Workstation's Route

On Linux: `register static linuxtarget05 --host 192.168.122.1 --port 48913 --check http` and
`publish linuxtarget05 --scope yard`. The Linux CLI reported Reachability `ok` and `Every check passed`.

- Windows `catalog` listed `linux-win-peer linuxtarget05 healthy linuxtarget05.linux-win-peer.lvh.me`.
- Windows Consul (`/v1/health/service/linuxtarget05?dc=linux-win-peer`) gave node `linux-win-peer`, address
  `192.168.122.1`, port 48913, and checks `serfHealth` and the service check `passing` with
  `HTTP GET http://192.168.122.1:48913/: 200 OK Output: linux-native-yard-target`.
- `curl.exe -H 'Host: linuxtarget05.linux-win-peer.lvh.me' http://127.0.0.1:49380/` returned HTTP 200 and the body
  `linux-native-yard-target`. Host `linuxtarget05.lvh.me` returned 404.
- The first run and the final run (`05-final-routes.out`) each succeeded on the first attempt.

`lvh-yard test` on the Linux or Windows side does not take a Remote Service name, so no Reachability result was
recorded for the Remote Services. The Consul Health and the actual responses above are the evidence.

## Out-of-range handling

Windows Instance in the WSL range (`05-out-of-range.out`): a Service `winvm05` with a Static Instance at
`172.19.144.1:49350` and Yard Scope. Its check was `critical` (the address is not reachable from the Docker VM).
The Linux peer listed `winvm05 unhealthy`, had no Traefik router for it, and returned HTTP 404 for Host
`winvm05.win-yard-05.lvh.me`, while `winweb05` kept its Route. A failed check alone can explain that result, so
the next scenario isolates the range rule.

Healthy Instance in the range, Linux origin (`05-out-of-range-linux-origin.out`): a Python server I started as a
temporary process on the Linux bridge address `172.17.0.1:48915` (no container). Service `linuxoor05` with a Static
Instance there and Yard Scope was healthy on Linux (`passing,passing`). Its own Linux Route returned HTTP 200 and
`linux-bridge-yard-target`. On Windows, Consul showed the remote Instance `172.17.0.1:48915` as `passing`, and a request
through the Windows Traefik for `linuxoor05.linux-win-peer.lvh.me` returned HTTP 404, while the control
`linuxtarget05.linux-win-peer.lvh.me` returned 200. Existing Out-of-range handling is preserved.

## Local listeners stay local after the join

`bin/05-lan-probe.py`, run on the Linux computer with the Windows Workstation joined (`05-lan-probe.out`):

| Probe to `192.168.122.117`                                                 | Result                  |
| -------------------------------------------------------------------------- | ----------------------- |
| TCP 49350 (explicit IPv4 listener control)                                 | OPEN                    |
| TCP 49386 (Consul server RPC)                                              | OPEN                    |
| TCP 49388 (Consul WAN gossip)                                              | OPEN                    |
| TCP 49387 (Consul LAN gossip)                                              | OPEN                    |
| TCP 49380 Traefik, 49381 dashboard, 49382 Consul HTTP, 49383 Traefik admin | no connection (timeout) |
| TCP 49389 Consul DNS, 49390 gRPC TLS                                       | no connection (timeout) |
| UDP 49389 (a DNS query to Consul DNS)                                      | no reply                |

The dashboard, proxy, Consul HTTP and Traefik admin ports stayed unreachable from the other computer. The
positive controls prove the client path works. `netstat` on Windows showed 49380-49383, 49389 and 49390 on
`127.0.0.1` only.

## Ports and protocols observed

- TCP 49386 (server RPC), 49387 (LAN gossip) and 49388 (WAN gossip): connected from Linux. Linux Consul also
  opened its own connections to 49386 for cross-datacenter reads, and memberlist logged a TCP stream from
  `192.168.122.117` on the Linux side.
- UDP 49388: Consul's `consul.memberlist.udp.received` and `.sent` counters for the WAN pool were non-zero on both
  agents in each 10 s sample (`05-udp-gossip-counters.out`: Linux received 2 and 6, sent 6 and 6; Windows
  received 5 and 4, sent 3 and 1). With two WAN members, received datagrams come from the other Workstation.
- A UDP request and reply test failed through Docker Desktop. A Python UDP echo container on
  `0.0.0.0:49360` received the datagram from Windows loopback and from Linux (its log showed both, with source
  `127.0.0.1`), but neither sender received the echo. The same container echoed inside the VM. This was a
  measurement of the forwarder, not of Consul. Do not rely on request and reply UDP services behind Docker Desktop
  host networking. The container was removed afterwards.
- Windows Firewall rules were not changed. Existing Allow rules for Docker's backend were in force; I did not
  test with them removed.

## Load and stalls

During the later checks the VM ran several Workstation stacks from parallel tickets on 2 CPUs.
I observed:

- `lvhy-win-peer-consul-1` logged `memberlist: Failed fallback TCP ping: timeout 5s ... ->192.168.122.117:49388:
i/o timeout` at 17:45:51, 17:49:01, 17:52:11 and 17:52:41 (the only errors in its log; its level is `error`).
- Cross-datacenter reads from the Linux agent (`/v1/health/service/winweb05?dc=win-yard-05`) returned in 0.01 to
  0.3 s most of the time, but stalled 1 to 12 s in bursts, once with HTTP 500 (`05-wan-read-loop.out`: 21 of 34 samples
  flagged; `05-wan-read-loop-2.out`: 5 of 22; `05-lowlevel-latency.out`). Reads from the Windows agent to Linux
  stalled too, sometimes in the same seconds (`05-reverse-read-loop.out`, `05-reverse-read-loop-2.out`). Reads of
  the local datacenter stayed under 1 ms.
- Direct TCP connects to port 49386 stayed at 4 ms. A direct HTTP request to the Python listener varied from 0.02
  to 1.3 s.
- After two failed cross-datacenter reads in a row, the Linux dashboard marks the Workstation unreachable
  (`DatacenterReads`' circuit breaker) and Traefik drops its Routes until a later read succeeds. I saw
  `win-yard-05: Workstation unreachable` and HTTP 404 on the Linux Route for about a minute at 18:49 and in short
  bursts later, and the Route came back each time without any change.
- The Windows dashboard logged `ConsulException` and `Docker cannot be followed` warnings in the same period,
  and CLI calls timed out at 10 s. These were load timeouts.

I did not isolate the cause. The load is a plausible cause; the Docker backend forwarding is not excluded. A rerun
on a quiet VM, and one with the Docker backend Allow rules and the UDP forwarder measured separately, belongs to
the final verification ticket.

## Check results

`mise run test:unit` and `mise run check` passed on this branch after the template and docs change (check exit
status 0, `cli:check` reported 0 lint issues). No automated test asserts the Compose bind text, because it would
only compare source text. The runtime results above are the acceptance evidence.

## Retained fixture

- After the proofs I deleted my test Services (`winweb05`, `winvm05` on Windows; `linuxtarget05`, `linuxoor05` on
  Linux), removed `lvhy-win05-web` and `lvhy-win05-udp`, stopped the temporary Python server on `172.17.0.1:48915`
  and ended my `consul monitor` streams.
- Windows project `lvhy-win-05` (Consul, Traefik, dashboard) and its volume `lvhy-win-05_consul-data`, with
  folder `C:\Users\yarddev\lvh-dev\05\workstation`, keep running. The CLI is
  `C:\Users\yarddev\lvh-dev\05\lvh-yard.exe`. Ticket 05 owns their cleanup.
- Linux peer `lvhy-win-peer` is running and joined. Its `.env` now has
  `YARD_PEERS=192.168.122.117:49388`, set with `ws yard peers --sync` and applied with `up -d`. Both Consul agents
  listed two WAN members `alive` at the end. The orchestrator owns the peer.
