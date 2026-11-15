# Unpublished container routing evidence

Containers without published ports, on unrelated Docker bridge networks, became Bound Instances on the real
Windows Workstation. Consul checks, the dashboard Reachability test and real requests through Traefik passed in one
full run. Stopping, starting, address changes, recreation and Compose `down`/`up` all moved the Bound Instance to the
new address without any manual edit. No product code needed a change.

## Environment

Observed on 2026-11-15:

- Windows 11 Pro x64 VM `192.168.122.117`, ordinary account `t-x\yarddev`, Windows PowerShell 5.1 through `win-ps`.
- Docker Desktop with Linux engine 29.9.3, WSL2 backend, host networking on. `docker info` reported 4 CPUs and
  6.2 GB. The VM had been raised and restarted by the maintainer, so all containers were recreated from scratch.
- Only this ticket's stack ran. Images: `hashicorp/consul:2.1.0`, `traefik:v3.7.16`, `traefik/whoami:latest`,
  `lvh-probe:1` (Python HTTP server on `0.0.0.0:8080`), and the dashboard image `lvhy-win04-dashboard:acceptance`
  (a tag of the ticket 03 image; no dashboard code has changed since).
- CLI: `GOOS=windows GOARCH=amd64 go build ./cmd/lvh-yard` of this branch after merging `windows-11` (68212e5).
- Workstation `win-native-04`, project `lvhy-win-04`, folder `C:\Users\yarddev\lvh-dev\04\workstation`, made new by
  `lvh-yard.exe ws init --dir ... --workstation win-native-04 --address 192.168.122.117`. Ports 49200 (Traefik),
  49201 (dashboard), 49202 (Consul HTTP), 49203 (Traefik admin). Local Scope, `YARD_PEERS` empty. The stack was
  healthy 29 s after `docker compose up -d --wait`.
- Harness: `bin/test-workstation-04-setup.ps1` and `bin/test-workstation-04.ps1`. Raw output of the full run:
  `evidence/04-run3.out`. Both are under `/home/ppalazon/.local/state/lvh-yard-windows-11/`, outside the repository.

A single run of `test-workstation-04.ps1` ran every scenario below in order and ended with
`ACCEPTANCE 04 SCENARIOS PASSED`. Earlier agents ran parts of it on the 2-CPU VM and hit stalls; this result is the
first complete run, on the larger VM.

## Fixtures

| Service     | Selector                                      | Container                              | Network                   | Docker address | `PortBindings` |
| ----------- | --------------------------------------------- | -------------------------------------- | ------------------------- | -------------- | -------------- |
| `svc04a`    | container name `lvhy-win04-a-web`, port 8080  | `docker run --network lvhy-win04-a`    | `lvhy-win04-a`            | 172.19.0.2     | `{}`           |
| `svc04b`    | container name `lvhy-win04-b-web`, port 8080  | `docker run --network lvhy-win04-b`    | `lvhy-win04-b`            | 172.20.0.2     | `{}`           |
| `svc04def`  | container name `lvhy-win04-default`, port 80  | `traefik/whoami` on the default bridge | `bridge`                  | 172.17.0.2     | `{}`           |
| `svc04shop` | Compose project/service `lvhy-win04-shop/web` | `lvhy-win04-shop-web-1`                | `lvhy-win04-shop_default` | 172.21.0.2     | `{}`           |
| `svc04blog` | Compose project/service `lvhy-win04-blog/api` | `lvhy-win04-blog-api-1`                | `lvhy-win04-blog_default` | 172.22.0.2     | `{}`           |

`docker ps` listed `ports=[]` for every one of them except the whoami image, which lists its `80/tcp` as exposed but
not published. The Services were made with `lvh-yard service create` and bound with `lvh-yard register container ...`
and `lvh-yard register compose ... --port`.

## Results

### a. Bound Instance, Consul check, Reachability and a real request

Every Service had one Bound Instance at the container's bridge address and Container Port, with no Host Port.
The dashboard API showed `hostPort=` empty, `unpublishedContainerPort=True`, `outOfRange=False`.
The Consul check was `passing` for all five before any Publication. For `svc04a`, `GET /v1/health/service/svc04a`
returned the check `service:svc04a:lvhy-win04-a-web:8080` with `TCP connect 172.19.0.2:8080: Success`, and the
registered address `172.19.0.2:8080`.

After `lvh-yard publish <service> --scope local`, for each of the five Services:

- `GET /api/workstation/services/<name>/reachability` returned `passed=True`, `reached=True`, `routed=True` and
  `status 200` for the Route `<service>.lvh.me`.
- `lvh-yard test <service>` printed `ok Instance <address> (in the Route)`, `ok Route ...: answered 200` and
  `Every check passed`.
- A real request `curl -H "Host: <service>.lvh.me" http://127.0.0.1:49200/` returned the marker of the container
  (`marker-a-web`, `marker-b-web`, `marker-shop-web`, `marker-blog-api`) with HTTP 200. For `svc04def` it returned the
  whoami page with `Hostname: marker-default`, `IP: 172.17.0.2` and HTTP 200.
- The dashboard page `/services/win-native-04/svc04a/reachability` said "The Route uses the passing Instances of
  Container Binding to container lvhy-win04-a-web, port 8080", "Instance 172.19.0.2:8080" and
  "Connected directly. In the Route."

### b. Stop and start

For `svc04a`, `svc04b`, `svc04shop` and `svc04blog`, `docker stop` removed the Bound Instance (`Instances=0`) while
the Consul registration was still listed once (`consulRegistrations=1`; the Route answered `502 Bad Gateway`).
After `docker start` the Instance returned at the same bridge address, the check was passing, Reachability passed
and the Route returned the marker with 200 again. No command edited an address.

### c. The address changes

While `lvhy-win04-a-web` and `lvhy-win04-b-web` were stopped, a filler container took their address
(`172.19.0.2` and `172.20.0.2`). After `docker start` the containers got `172.19.0.3` and `172.20.0.3`. The Bound
Instances followed (`host=172.19.0.3:8080`, `host=172.20.0.3:8080`), the checks passed, Reachability passed and the
Routes returned `marker-a-web` and `marker-b-web` with 200. The harness fails when the address does not change, so
this exercised an address update on both networks.

### d. Recreate

- `docker rm -f` then `docker run` of `lvhy-win04-a-web` removed the Instance and then brought it back at
  `172.19.0.2:8080`; check, Reachability and Route 200 passed.
- `docker compose up -d --force-recreate`, `down` and `up -d` for the shop and blog projects: after recreate and
  after `up`, each had one Instance at its address (`172.21.0.2:8080` and `172.22.0.2:8080`) with a passing check,
  passing Reachability and the marker Route 200. After `down` each had `Instances=0` and the Route answered 502.

### e. Scope rules

- Under Yard Scope (`svc04a`, `svc04shop`) the Bound Instance stayed at the container address
  (`172.19.0.2:8080`, `172.21.0.2:8080`), the Consul check stayed passing and the Route
  `<service>.lvh.me` answered 200. The CLI printed `risk: The Bound Instance at 172.19.0.2:8080 is on a container
port that is not published, so other Workstations cannot reach it.` and the Service had the warning
  `unpublished-container-port`.
- The own Workstation never marks its own Instance out of range (`outOfRange=False` for all Instances above,
  172.17-172.22). The Out-of-range rule for a remote Workstation was not changed and was not re-measured here. Ticket 06
  measured it with the Linux peer: an unpublished container at `172.17.0.7:80` under Yard Scope gave a Linux Route of
  404, `outOfRange=true`, warning `unpublished-container-port` and a skipped Linux test
  ([06 evidence, section c](06-route-published-container-ports.md)). This run had no peer.
- A bridge address was reached only from Windows itself, where Traefik runs with host networking. I did not test
  that another computer reaches it, and the user guide does not claim that it does.

## Open points

- None for the criteria in the ticket. Out-of-range for a remote Workstation rests on ticket 06's measurement,
  because this run had no peer; the rule's code is unchanged by this ticket.
- The "dashboard Reachability" check was observed through the API, the CLI `test` command and the page text for
  `svc04a`. I did not capture a screenshot.

## Product code and tests

No product or dashboard code changed. The user guide gained one paragraph in the Container Binding section:
a container with no Host Port needs no published port and no shared network, its address is private to this
computer, and Out-of-range and the Service warning still apply under Yard Scope.

## Cleanup

The stack `lvhy-win-04` and the test containers were stopped, not removed. The volume `lvhy-win-04_consul-data` and the
networks `lvhy-win04-a` and `lvhy-win04-b` remain.
