# Published container ports across scopes: evidence

Real containers with published ports were bound on the Windows Workstation of the mixed-platform Yard. Each case
below records the Docker mapping, the Consul registration and check, the dashboard Reachability result and real
requests, on Windows and from the Linux peer. No product rule needed a change. The product code was correct in
every measured case. This ticket adds two dashboard component tests and the Windows port-binding section of the
user guide.

The shared VM was overloaded during the run (see [Load and stalls](#load-and-stalls)). Every result below is what I
observed. Where a first attempt failed because of a stall, the text says so.

## Environment

Observed on 2026-11-14:

- Windows 11 Pro x64 VM `192.168.122.117`, ordinary account `yarddev`. Docker Desktop 4.99.0, Linux engine 29.9.3,
  WSL2 backend, host networking on. The VM has 2 CPUs and 3.9 GB.
- The ticket 05 Workstation `win-yard-05` (project `lvhy-win-05`, CLI `...\05\lvh-yard.exe`, Traefik 49380,
  dashboard 49381, Consul HTTP 49382, admin 49383). It was joined to the Linux peer in both directions. No product
  code changed, so I reused its running images, including the ticket 03 dashboard image.
- Linux peer `linux-win-peer` (`192.168.122.1`, CLI endpoint `http://127.0.0.1:48780`, Traefik `48380`, Consul
  HTTP `48500`).
- Test images: `traefik/whoami:latest` for published ports and `lvh-probe:1` (Python HTTP server on `0.0.0.0`) for
  the host-network case. Containers `lvhy-win06-*`, Services `pub06*`, ports 49500-49512.
- Harness: `/tmp/win06/*.sh` (not kept). Every Windows command ran as `yarddev` in Windows PowerShell 5.1 through
  `win-ps`. Yard-scope Route name is `<service>.win-yard-05.lvh.me`; the Local Route is `<service>.lvh.me`.

## Mapping and results

"Win" is a request from the Windows computer. "Linux" is a request from the Linux peer to the Windows LAN address.
Every request returned the whoami (or marker) page unless it says otherwise. A code of `000` is a refused or timed
out connection.

### a. Wildcard `-p 49500:80`

Docker Desktop lists `0.0.0.0:49500->80/tcp, [::]:49500->80/tcp`. `docker inspect` gave HostIp `0.0.0.0` and `::`.

| Scope | Registered              | Consul check                                     | Win direct 127.0.0.1 / LAN | Linux direct | Win Route | Linux Route                           |
| ----- | ----------------------- | ------------------------------------------------ | -------------------------- | ------------ | --------- | ------------------------------------- |
| Local | `127.0.0.1:49500`       | passing (`TCP connect 127.0.0.1:49500: Success`) | 200 / 200                  | 200          | 200       | 404, Instance skipped as out of range |
| Yard  | `192.168.122.117:49500` | passing                                          | 200 / 200                  | 200          | 200       | 200                                   |

- Windows `lvh-yard test`: `ok Instance ... (in the Route)`, `ok Route pub06any.lvh.me: answered 200`, exit 0, in
  both scopes. Linux `lvh-yard test pub06any --workstation win-yard-05` under Yard: both `ok`, the Route
  `pub06any.win-yard-05.lvh.me` answered 200.
- Under Local, the Linux catalog listed the Service with no Route name. The Linux test said `skip Instance
127.0.0.1:49500: out of range` and `Local Scope on win-yard-05, so only its Instances are tested`.
- The Instance had no warning in either scope (`/api/services/win-yard-05/pub06any`, `warnings=[]`).

### b. Explicit bindings

Loopback `-p 127.0.0.1:49501:80` (Docker lists `127.0.0.1:49501->80/tcp`):

| Scope | Registered               | Win direct 127.0.0.1 / LAN | Linux direct | Win Route | Linux view                                                                                                 |
| ----- | ------------------------ | -------------------------- | ------------ | --------- | ---------------------------------------------------------------------------------------------------------- |
| Local | `127.0.0.1:49501`        | 200 / 000                  | 000          | 200       | Route 404, `skip ... out of range`                                                                         |
| Yard  | `127.0.0.1:49501` (kept) | 200 / 000                  | 000          | 200       | Route 404, warning `loopback-instance`, `outOfRange=true`, `skip ... out of range`, `No Instance in range` |

The Windows dashboard showed the warning `loopback-instance` and Windows `test` passed (`ok Instance 127.0.0.1:49501
(in the Route)`, Route 200). The explicit loopback binding was never widened: the Windows LAN address and the Linux
peer both failed to connect at every step.

LAN `-p 192.168.122.117:49502:80` (Docker lists `192.168.122.117:49502->80/tcp`):

| Scope | Registered                     | Win direct 127.0.0.1 / LAN | Linux direct | Win Route | Linux view                                          |
| ----- | ------------------------------ | -------------------------- | ------------ | --------- | --------------------------------------------------- |
| Local | `192.168.122.117:49502` (kept) | 000 / 200                  | 200          | 200       | Route 404 (Local), Instance `ok` in the Linux test  |
| Yard  | `192.168.122.117:49502`        | 000 / 200                  | 200          | 200       | Route 200, test `ok` for the Instance and the Route |

The explicit LAN binding stayed unreachable at `127.0.0.1` from Windows under both scopes. Local Scope did not
rewrite it to loopback, because loopback would fail.

Mixed `-p 127.0.0.1:49511:80 -p 192.168.122.117:49512:80` on one container: Local registered
`127.0.0.1:49511` (Win Route 200, Linux Route 404, Linux direct to 49511 `000`). After `publish --scope yard` it
registered `192.168.122.117:49512` (Win direct at LAN 200, at loopback `000`, Linux direct 200, Linux Route 200).
The Linux `lvh-yard test` for this Service answered "Workstation win-yard-05 is unreachable" at that moment; see
[Load and stalls](#load-and-stalls).

### c. Out-of-range Instances are not routed remotely

- Loopback `pub06loop` under Yard Scope (table above): the Linux Traefik gave 404, the Linux dashboard marked the
  Instance `outOfRange=true`, and the warning `loopback-instance` showed on both dashboards.
- `pub06lan` replaced by a loopback-only container under Yard Scope: registered `127.0.0.1:49504` (Consul check
  passing). Windows direct 127.0.0.1 200, LAN `000`. Linux direct `000`, Linux Route 404, Linux view warning
  `loopback-instance`, `127.0.0.1:49504 outOfRange=true`, test `skip`. The Windows Route returned 502 for the first
  check about 20 s after the replacement and 200 shortly after (see load notes).
- A container with no published port (`lvhy-win06-br`, bridge address `172.17.0.7`) under Yard Scope: registered
  `172.17.0.7:80`, Consul check passing, Windows Route 200 (Traefik reaches the bridge address from the host
  network), Linux Route 404, Linux view warning `unpublished-container-port`, `outOfRange=true`, test `skip`.
  The Windows dashboard printed the existing risk text while publishing.

### d. Scope change and replacement

- Scope change of `pub06any` Yard to Local: Consul moved from `192.168.122.117:49500` to `127.0.0.1:49500`, the
  Linux Route changed from 200 to 404 and the Linux test said `skip ... out of range`. Windows kept 200. Local to
  Yard gave `192.168.122.117` again and a Linux Route of 200. The same Local to Yard change moved `pub06loop`
  (stayed `127.0.0.1`, warning appeared) and `pub06lan` (stayed LAN) as the table in b shows.
- Replacement with a new Host Port: `docker rm -f` then `docker run -p 49503:80` under Yard Scope. Consul gave
  `192.168.122.117:49503`, direct requests to 49503 returned 200, the old port 49500 returned `000`, and both Routes
  returned 200 (Linux test `ok`, `answered 200`). A second replacement to 49506 was timed: Consul moved at
  t+7 s, the Windows Route was 404 during the gap (no Instance) and 200 at t+11 s. The Linux Route and test were
  200 with `192.168.122.117:49506` afterwards.
- Replacement with a new address: `pub06lan` from `192.168.122.117:49502` to `127.0.0.1:49504` (see c), then to
  `192.168.122.117:49505`. Consul followed each change, the Linux view went from `loopback-instance`, out of range
  and Route 404 back to no warning and Route 200 (Linux test `ok` for the Instance and the Route). The Windows Route
  returned 502 for the first request after the replacement and later 200; the Traefik API then listed only the new
  server `http://192.168.122.117:49505` UP, and the dashboard configuration showed the same URL.
- Restart that changes the Host Port: `docker run -p 80` (random port) gave `0.0.0.0:49735->80`. After `docker stop`,
  two filler containers and `docker start`, Docker Desktop mapped it to `0.0.0.0:49902->80`. Consul registered
  `192.168.122.117:49902`, direct requests at 49902 gave 200 on Windows and Linux, and the old port 49735 gave
  `000`. The Windows Route was 200. The Linux Route flapped (see load notes) and returned 200 on retry.

### e. Never a VM-only address

Every address registered for a published port was `127.0.0.1` or `192.168.122.117`. The only other address was the
bridge container address `172.17.0.7` of an unpublished container, which is inside the default Out-of-range list and
gave no remote Route. A container on the host network (`lvhy-win06-host`, Python on `0.0.0.0:49510`) registered
`192.168.122.117:49510`, with Windows Route 200, Linux direct 200 and Linux Route 200 under Yard Scope. I found no
use of `host.docker.internal`, `192.168.65.x` or WSL addresses in the dashboard, the CLI or the generated Compose
files (`grep` of those trees). The default Out-of-range list does not name the Docker Desktop address range
`192.168.65.0/24`, but no mapping of the published or host-network cases produced such an address.

## Component tests

Windows runtime rules are not new, so these tests record behavior that the real runs proved:

- `IPv4OnlyComponentTest.aDualStackWildcardPortFollowsTheScopeOnItsIpv4Address` uses `0.0.0.0` plus `::` as Docker
  Desktop lists it. It checks the LAN address with no Publication, `127.0.0.1` under Local, the LAN address under
  Yard, and no warning.
- `BoundInstanceTargetsComponentTest.replacingAContainerMovesTheBoundInstanceToItsNewHostPortAndAddress` replaces a
  container under Yard Scope: wildcard, loopback, then LAN. The Instance and the Traefik Route follow each change.

Both passed with `./mvnw -q test -Dtest='BoundInstanceTargetsComponentTest,IPv4OnlyComponentTest'`.

## Load and stalls

Tickets 04 and 07 ran their stacks at the same time. Observed effects:

- The Windows CLI timed out at 10 s for most `publish` and some `service delete` commands ("The dashboard ... did not
  answer within 10 s"). The change was applied each time; a later read showed it. I did not count on the CLI output.
- The first Windows Route request after a replacement returned 502 or 404 for 20 s or more in the first two runs. A
  later timed run took 11 s.
- The Linux Traefik logged `cannot fetch configuration data ... context deadline exceeded` from the Linux dashboard
  while the Linux dashboard waited for the Windows datacenter. The Linux Route to a Windows Service then flapped
  between 200 and 404 for about a minute and recovered. A Linux test once said "Workstation win-yard-05 is
  unreachable: its Consul does not answer". These stalls started with cross-datacenter reads and are not diagnosed.
  I did not change any rule for them.

## Cleanup

All `lvhy-win06-*` containers and all `pub06*` Services were removed. The Windows Workstation `lvhy-win-05` was left
running for the next ticket. The Linux peer was not changed.
