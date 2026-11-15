# Ticket 09 (phase A) and batch B runtime checks: evidence

Real Windows lifecycle, Backup, project-isolation and cross-platform transfer checks on the Windows 11 VM.
The Docker Desktop restart check is **pending**: the maintainer restarts Docker Desktop, then runs the prepared script
(see [Docker Desktop restart](#docker-desktop-restart-pending-maintainer-restart)). Nothing below claims it.

## Environment

Observed on 2026-11-15.

- Windows 11 Pro x64 VM `192.168.122.117`, account `t-x\yarddev` (ordinary user), Windows PowerShell 5.1.26100.9818.
- Docker engine 29.9.3 (client and server), context `desktop-linux`. Docker Desktop 4.99.0 and Compose 5.7.0 from the notes of
  `/home/ppalazon/.local/state/lvh-yard-windows-11/notes/README.md`.
- Windows CLI `lvh-yard.exe` built on Linux from this branch (`win11/09-persistence` at ff8aa64), `-X main.version=dev`.
  The 9.8.7 and 9.8.8 packages come from `dev/package-cli.sh` as CI does.
- Dashboard image built from this branch: `docker build -t lvhy-win09-dashboard:acceptance .`, image ID
  `aab18c6dd479` on Linux, loaded with `docker save`, `win-scp`, `docker load`. It runs on the VM as `3dc961c56643`.
  Every Windows Workstation below runs it through `YARD_DASHBOARD_IMAGE` in `.env`.
- Linux side: this host. Throwaway Linux Workstations `lin-09a` (project `lvhy-lin-09a`, ports 49650 to 49660) and `lin-09b`
  (`lvhy-lin-09b`, ports 49670 to 49680), the same dashboard image, CLI built from this branch for Linux. They are removed.
- Windows fixtures: `lvhy-win-09a` (folder `C:\Users\yarddev\lvh-dev\09\a\workstation`, ports 49600 to 49610) and
  `lvhy-win-09b` (folder `...\09\b\workstation`, ports 49620 to 49630).
- All scripts: `/home/ppalazon/.local/state/lvh-yard-windows-11/bin/09-*.ps1`. Raw output:
  `.../evidence/09-*.out`. Reruns after a script bug keep the first output under a `run1-script-bug` name.

## Part 1: batch B runtime checks

### 1a. Windows-only tests, run natively

`dev/test-windows-native.ps1 -PowerShellMajor 5` as `yarddev`, package built like the CI `windows-package` job. The VM
output is `C:\Users\yarddev\lvh-dev\09\native.out`. The VM has no `pwsh.exe` on PATH, so the process PATH
got `C:\Users\yarddev\lvh-dev\01\pwsh7` for this run only (not the machine or user PATH).

```text
Shell 5.1.26100.9818; account t-x\yarddev; ordinary user; no Unix tool on the PATH
PASS packaged executable: integration tests, including both PowerShells
--- PASS: TestInitWritesNewWorkstationFolder (0.04s)
--- PASS: TestWindowsInitTakesTheDetectedAddressWithoutATerminal (0.04s)
--- PASS: TestWindowsInitRequiresAddressWhenNoAdapterHoldsTheDefaultRoute (0.05s)
--- PASS: TestWindowsInitNamesTheComposeProjectOfEachFolder (0.08s)
--- PASS: TestWindowsSetsTheInstanceHostApartFromTheAdvertisedAddress (0.07s)
PASS unit tests: 257 passed, 7 skipped
PASS installer acceptance
```

The script ran in 46 s with exit code 0. The three tests of batch B ran natively and passed.

### 1b. Compose project name

`09-part1b.ps1`, `evidence/09-part1b.out`. Two fresh folders with the basename `ws` under different parents:
`...\09\p1\x2\same\ws` and `...\09\p1\y2\same\ws`, each from a real `lvh-yard ws init`.

- `COMPOSE_PROJECT_NAME=ws-d50a0e9b` and `ws-1398db4e`. They differ.
- `docker compose config --quiet` exited 0 for both. `docker compose config` printed `name: ws-d50a0e9b`,
  `volumes: consul-data: name: ws-d50a0e9b_consul-data`, and the same for `ws-1398db4e`.
- `docker compose up --no-deps consul-data-init` created two volumes, `ws-d50a0e9b_consul-data` and
  `ws-1398db4e_consul-data`. They differ. `down --volumes` in each folder removed them. The volume list before and after
  held only the five older fixture volumes (`lvhy-win-03`, `-04`, `-05`, `-07`, `-08`).
- First run `09-part1b-run1-script-bug.out`: my script looked for the name `same-*` but the basename was `ws`. It printed two
  FAIL lines from that script error. The names there were `ws-81367f1f` and `ws-4ecf44ec` (different; `docker compose`
  accepted both, and the volume list matched afterwards). The rerun used the correct pattern.

### 1c. `YARD_INSTANCE_HOST` empty, then set

`09-part1c.ps1`, `evidence/09-part1c.out`. Project `lvhy-win-09b`, `.env` written by `ws init` with
`YARD_BIND_ADDRESS=192.168.122.117` and `YARD_INSTANCE_HOST=` (empty).

- Empty: `docker exec <dashboard> printenv YARD_INSTANCE_HOST` printed `192.168.122.117`. `docker compose config` printed
  `YARD_INSTANCE_HOST: 192.168.122.117`.
- With a native PowerShell listener on `0.0.0.0:49630`, `register static ih-empty --port 49630` (no `--host`) printed
  `Registered ih-empty at 192.168.122.117:49630`. `publish ih-empty --scope local`, then `test`: Instance and Route `ok`,
  Route answered 200, and `curl -H "Host: ih-empty.lvh.me" http://127.0.0.1:49620/` returned `instance-host-empty`.
- `lvh-yard ws set instance-host 127.0.0.1` saved `YARD_INSTANCE_HOST=127.0.0.1`; `ws compose recreate` followed. The
  dashboard container then printed `YARD_INSTANCE_HOST=127.0.0.1`. The Consul container still got
  `YARD_BIND_ADDRESS=192.168.122.117`.
- With a native listener on `127.0.0.1:49631`, `register static ih-set --port 49631` printed `Registered ih-set at
127.0.0.1:49631`. Local Publication: Reachability `ok`, Route 200, response body `instance-host-set`.
- `ws set instance-host not-an-address` exited nonzero: `invalid instance-host: use an IPv4 address such as 192.168.1.20, or
an empty value to use bind-address`.
- After the recreate, `ih-empty` kept its saved Instance at `192.168.122.117:49630` (critical: its listener had stopped with the
  script job), which shows saved setup survived that recreate.

## Part 2: persistence and transfer

### Setup of `lvhy-win-09a`

`09-a-setup.ps1`, `09-a-setup2.ps1`, `09-a-setup3.ps1` (the first two stopped on script errors that the next one fixed: a missing
`service create box09`, and `alias add` needs a Yard peer, so the Alias came from a Backup import instead).

Created by `ws init --workstation win-yard-09a --address 192.168.122.117`, then edited like a user:

- `.env`: `COMPOSE_PROJECT_NAME=lvhy-win-09a`, `YARD_DASHBOARD_IMAGE=...`, ports, and a line `MY_NOTE=keep-me`.
- `compose.yaml`: traefik image `traefik:v3.7.15` (the tag is an alias of `traefik:v3.7.16` made with `docker tag`, so
  the "older" image has the same content), a top comment, an extra storage line `user-scratch:/scratch` on
  `consul-data-init` with a top-level `user-scratch` volume, and a user network `usernet` on `consul-data-init`.
- User files `consul.d\50-user.hcl` and `traefik\dynamic\50-user.yaml`.

Persistent setup (CLI): Route Profile `team-headers` (a response header `X-Persist: win09`), Service `web09` (Static Instance
`127.0.0.1:49690` with an HTTP check, tag, meta, plus a Container Binding to `lvhy-win09-box:80`, Instance Priority on),
Service `box09` (Container Binding), Service `chat09` (an Alias to `remote-ws/chat`, imported from a Backup file),
Local Publications of `web09` and `box09` with `team-headers`, and a Yard CA (`ws tls ca create`, so a private key exists).
`web09.lvh.me` and `box09.lvh.me` answered 200 with the `X-Persist: win09` header through Traefik on 127.0.0.1:49600.

### Stop/start, recreate, image upgrade

`09-a-lifecycle.ps1`, `evidence/09-a-lifecycle.out` (the first run `...-run1-script-bug.out` stopped on a script bug in a check;
the rerun passed all checks). After each operation the script compared the Backup export of the saved setup (without its
`at:` line), the Route Profile, the volume creation time, the Consul node ID, the Raft last log index, the Consul process
user, and requested both Routes.

| After                                           | Backup export hash | Volume `CreatedAt`     | Consul node ID                         | Raft last index | Consul uid |
| ----------------------------------------------- | ------------------ | ---------------------- | -------------------------------------- | --------------- | ---------- |
| Base                                            | `A9D29D14F62BA922` | `2026-11-15T10:50:20Z` | `e7ab7fc4-3efc-e7e9-ae53-1e2932303389` | 86              | 1000       |
| `ws compose stop`, `ws compose start`           | same               | same                   | same                                   | 96              | 1000       |
| `ws compose recreate` (new containers)          | same               | same                   | same                                   | 106             | 1000       |
| `ws compose upgrade` (9.8.7 CLI) and `recreate` | same               | same                   | same                                   | 118             | 1000       |

- After stop, no container of the project ran and the volume existed. Start kept the Consul container (`482ec28ce6f1`).
  Recreate made new ones (consul `482ec28ce6f1` to `390a02461d59`, dashboard `145064110ffe` to `ae3258e90ead`).
- Consul's PID 1 was `consul` with uid 1000. The volume root was owned by `1000:1000` and held `node-id raft serf ...`.
  The node ID did not change, so Consul read the retained state.
- Upgrade with the 9.8.7 CLI: `.env`, `lvh-yard.yaml`, `consul.d\50-user.hcl` and `traefik\dynamic\50-user.yaml` had the
  same SHA-256 before and after. A line appended to the generated `consul.d\00-lvh-yard.hcl` was gone after the upgrade
  (the file equaled its first hash again). `compose.yaml` differed in exactly two lines, both `image:` lines:
  `traefik:v3.7.15` to `traefik:v3.7.16` and the dashboard default `:latest` to `:9.8.7`. The top comment, `usernet` and
  `user-scratch` edits stayed, and `docker compose config --quiet` passed.
- After the upgrade and recreate, the Traefik container's image was `traefik:v3.7.16`; `consul-data-init` was on
  `lvhy-win-09a_usernet`; the volume `lvhy-win-09a_user-scratch` existed. The dashboard image did not change, because
  `YARD_DASHBOARD_IMAGE` in `.env` (user-owned) overrides the default line.
- The generated `.env` carries `COMPOSE_PROJECT_NAME`. The upgrade kept it.

### Backup export into the protected folder, and what it holds

`09-backup-export.ps1`, `09-private-read.ps1`, `evidence/09-backup-export.out`, `09-backup-export-checks.out`,
`09-private-read.out`.

- `lvh-yard export -o C:\...\a\workstation\backups\win09a-cli.yaml` wrote it. `POST /backups/new` on the dashboard returned
  HTTP 303 and wrote `win-yard-09a-<time>.yaml` into the same folder. The import in setup wrote a safety Backup there too.
- `icacls` on the folder and the CLI Backup listed `t-x\yarddev:(F)`, `NT AUTHORITY\SYSTEM:(F)` and
  `BUILTIN\Administrators:(F)` only.
- As `yardother` (an ordinary account): a control file with an explicit `BUILTIN\Users:(R)` read passed. `Get-Content` of the
  Backup, of `.env` and of `ca\ca.key` each failed with `UnauthorizedAccessException`. (My first run put the control file where
  it was not readable; the `09-control-owner.ps1` run fixed the control file, and the rerun passed.)
- The Backup YAML: version 3; holds `web09`, `box09`, `chat09`, `team-headers`, the Container Binding as
  `container: lvhy-win09-box`, the Static Instance host. The text had no Health state, no Bound or Foreign Instance, no PEM or
  private key text (the first body line of `ca.key` is absent), no Gossip Key, and no Consul data names. 13 checks passed.
  The Backup also holds LVH Yard's own Services (`consul-ui`, `traefik-admin`, `yard`) with their loopback Static Instances.

### Windows to Linux

`evidence/09-win-to-linux.out`, `09-win-to-linux-absent.out`. The file went with `win-scp` to the host and into a fresh Linux
`lin-09a` (`lvh-yard import --dry-run`, then `import`).

- `Added 14, skipped 10`. The Linux catalog showed `web09` (Static Instance, Container Binding), `box09` (Container Binding
  only: `unavailable`, no Instance because no such container exists there), `chat09` (the Alias, unpublished), the profile
  `team-headers` and the Publications. The Linux Backup export had the same entries.
- Not transferred: the Windows KV marker `lvhy-win09/marker` was HTTP 404 in Linux Consul; its node list held only `lin-09a`; no
  `ca/` and no file in `tls/` on the Linux side; the Windows Gossip Key stayed out of the file and the Linux key out of the
  Windows Backup.
- Observation: the Backup holds the own Services of the source. Ports on the two Workstations differed here, so the import added
  the Windows `consul-ui`, `traefik-admin` and `yard` loopback Static Instances to the Linux own Services. They were
  `critical` (nothing listens there). The user guide now says so. With equal ports they are `skip` lines.

### Linux to Windows

`evidence/09-linux-setup.out`, `09-remove-reinit.out` (`run2-script-bug` shows the first failures). Linux `lin-09b` got a
profile `lin-headers`, Services `lin-web` (a Static Instance without host, a Container Binding, a tag and meta), `lin-box`,
`lin-chat` (an Alias), Local Publications, a Foreign Instance `foreign-1` at `10.9.9.9:81` registered straight in Consul, and a
KV marker. Its Routes answered 200 with `X-Persist: lin09`. `lvh-yard export` wrote the Backup, which `win-scp` copied to
the VM. A freshly configured Windows Workstation (`ws init` in a new folder, ports and project name as 09b, started with
`ws compose start`) then imported it (`import --dry-run`, `import`).

- Arrived: `lin-web`, `lin-box`, `lin-chat`, the profile `lin-headers`, the Container Binding by container name `lvhy-lin09-box`,
  the Alias, the Route Profile header text, and the Static Instance with no host, which became `192.168.122.117:49680` (the
  Windows LAN address).
- Not transferred: no `foreign-1` or `10.9.9.9` in the catalog; no Bound Instance (`172.17.0.x:80 passing lin-...`); the KV marker
  `lvhy-lin09/marker` gave HTTP 404 in Windows Consul; Windows Consul listed only its own node `win-yard-09b`; the folder had no
  `ca\` and no file under `tls\`; the Windows Gossip Key was not in the Linux Backup.

### Reset of one project

`09-reset.ps1`, `evidence/09-reset.out` (the first run `...-run1-script-bug.out` stopped on a script error before the reset).
`lvhy-win-09b` had services `ih-empty`, `ih-set`, a user volume `lvhy-win-09b_user-scratch` and a Backup `before-reset.yaml` that
`lvh-yard export -o` wrote into its folder. `docker compose ls` listed `lvhy-win-09a` and `lvhy-win-09b`. From the folder of 09b:
`docker compose down --volumes` exited 0.

- Volumes before: the five older fixtures, `lvhy-win-09a_consul-data`, `lvhy-win-09a_user-scratch`, `lvhy-win-09b_consul-data`,
  `lvhy-win-09b_user-scratch`. After: the same list without the two `lvhy-win-09b` volumes. Every remaining volume kept its
  `CreatedAt`. No container or network of 09b remained.
- The five generated files of the 09b folder had the same SHA-256 before and after, and `backups\before-reset.yaml` was still
  there.
- Project 09a after the reset: containers healthy; export hash `A9D29D14F62BA922`, node ID, volume `CreatedAt` unchanged;
  Raft index 133 to 134; both Routes answered 200 with the header.
- `ws compose start` on 09b: its catalog showed only `consul-ui`, `traefik-admin` and `yard` (the script's check for `yard:`
  with a colon used a wrong pattern and printed FAIL; the catalog lines themselves are in the output). Its Consul node ID
  changed (`777615b1-...` to `c2da864d-...`), the volume `CreatedAt` changed (`10:47:32Z` to `11:00:23Z`), Consul ran as
  uid 1000. `import before-reset.yaml` then added `ih-empty` and `ih-set` again.

### Folder deletion, and complete removal

`09-remove-reinit.ps1`, `evidence/09-remove-reinit.out` (two earlier runs stopped on script errors; their output is kept as
`run1` and `run2-script-bug`).

- Scratch project `lvhy-win-09c` (`ws init`, `up --no-deps consul-data-init`): after `Remove-Item -Recurse -Force` on its folder,
  `lvhy-win-09c_consul-data` still existed, the exited container remained, and `docker compose ls -a` still listed the project
  with the missing `compose.yaml` path. `docker compose -p lvhy-win-09c down --volumes` exited 0 and left no volume,
  container or network of that project.
- Complete removal of 09b (`docker compose down --volumes` in the folder, then deleting the folder): no volume, container or
  network of `lvhy-win-09b` remained, and the folder was gone. The image list (name and ID) was identical before and after.
  09a's export hash, node ID and volume `CreatedAt` were unchanged.
- The script's check "no volume of any other project was removed" printed FAIL. Its baseline had been taken after a first failed
  run left `lvhy-win-09c_consul-data` behind; that volume was the one that the p3 cleanup removed on purpose. The final
  `docker volume ls` listed the five older fixtures, `lvhy-win-09a_consul-data`, `lvhy-win-09a_user-scratch` and the fresh
  `lvhy-win-09b_consul-data`.

### Docker Desktop restart (pending maintainer restart)

Not run. State left for it:

- Project `lvhy-win-09a` runs (healthy). Helper containers `lvhy-win09-box` (whoami) and `lvhy-win09-static` (an HTTP server
  on `127.0.0.1:49690`) run. `lvhy-win-09b` is stopped, not removed.
- `C:\Users\yarddev\lvh-dev\09\a\pre-restart.json` records the export hash, node ID, volume `CreatedAt`, a KV marker written
  into Consul, all image IDs, volume names, networks and containers.
- After the restart, run `~/.local/state/lvh-yard-windows-11/bin/09-post-restart.sh`. It prints what survived, runs
  `lvh-yard ws compose start`, starts the helper containers if they are gone, and prints PASS or FAIL for each check.
  Output also goes to `evidence/09-post-restart.out`. It deletes nothing.
- I ran the script once without a restart as a rehearsal, so the script itself is proven on a stack that never stopped
  (`evidence/09-post-restart-rehearsal-without-restart.out`: all checks passed, 0 failures). That run is not the restart proof.

## Criteria

Docker Desktop is not restarted yet, so the first and last criteria stay open.

- [ ] Create persistent setup, then stop/start, recreate, upgrade images and restart Docker Desktop.
      Stop/start, recreate and upgrade observed. The restart is pending the maintainer.
- [x] Normal lifecycle operations preserve the project-scoped Consul volume; Consul stays non-root and uses the retained state.
- [x] Image upgrades keep user Compose network and storage edits, `.env` edits and user files; generated-file ownership held.
- [x] Backup export into the protected Workstation Folder; import on another platform and the reverse.
- [x] The setup of the Backup contract survives; no Bound Instances, Health, Foreign or Remote Services, private certificates
      or raw Consul data arrived. (The Backup format did not change.)
- [x] Docs: fresh setup plus Backup import for cross-platform moves; no promise of portable Compose files or raw Consul migration.
- [x] Documented destructive reset with a data-loss warning; it removed only the selected project's Consul state.
- [x] Docs: folder deletion leaves Docker state; reset, Backup transfer and complete removal are separate; a Backup does not
      archive private keys.
- [ ] Persistence and transfer verified through CLI and API on running Workstations, with Windows lifecycle, Backup write and
      isolation evidence. All of it recorded above except the Docker Desktop restart.
