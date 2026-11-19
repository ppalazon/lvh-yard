# User guide

LVH Yard lets you reach a development Service on any Workstation in your Yard
at a fixed hostname, such as `http://grafana.lvh.me`. You do not need its IP
address or port. [GLOSSARY.md](../GLOSSARY.md) defines the words, and
[docs/adr/](adr/) records the decisions.

Each Workstation runs three things:

- its own Consul datacenter, federated with the others
  ([ADR-0001](adr/0001-one-consul-datacenter-per-workstation.md))
- a Traefik on `127.0.0.1:80`
- the dashboard, which gives Traefik its Routes
  ([ADR-0002](adr/0002-traefik-reads-routes-from-lvh-yard.md))

Each page of the dashboard has a navigation bar at the top. It links to the
Catalog, Containers, Route Profiles and Backups pages, and it highlights the
current page. The header names Consul, Traefik and Docker with their Status.
A part that is OK and uses TLS shows a lock in place of the tick. Open the
summary to see the Status page. On a phone, the header shows their worst Status.

Every page works with the keyboard. The first Tab on a page shows a "Skip to the
main content" link, which moves past the header. Each control shows a ring when
it has focus. The Container field of the bind form is a list: Down and Up mark
an entry, Enter picks it, and Escape closes the list. The pages keep a contrast
of at least 4.5 to 1 for text in both themes.

The Status page shows one box for each of Consul, Traefik (when on) and Docker. Each box
gives the state and the reason. Next to the name of Consul and Traefik, a badge shows each
TLS that the part can have: green when it is on, red when it is off. Consul has
"TLS", which it uses when it checks the certificate of every Workstation. Traefik
has "HTTPS", "Client Certificate" and "Mutual TLS". A part that does not answer shows
no badge. Its Technical details list the facts behind the
state. When a part is not OK, its details start open. When a part is OK, they
start closed. The page refreshes by itself and keeps the details that you open
or close. On a phone, each detail stacks its name, state and value.

The Catalog shows one row for each Service, grouped by Workstation. Its columns
are Service, State, Scope, Hostname and Instances. On a phone, each row stacks these
facts without sideways scrolling. The Scope column says Local or Yard for a
published Service. The Instances column starts with the count,
such as "2 of 3 passing", or "No Instances". After it come a badge for each kind
of Instance Source that the Route uses. Each cell stays on one line on a wide
screen, and a long value ends in "...". Unpublished Services show the count too. Click
anywhere in a row to open the Service. Links in the row, such as the hostname,
keep their own click.
Instance addresses are on the Service page. You can filter by Workstation,
state and Scope. The Scope filter starts on "In range". It hides the Remote
Services that this Workstation has no Route to: those with Local Scope or no
Publication, and those with no Instance in range. Choose "Every scope" to see
all Services. Choose Local or Yard to see only the Services with that Scope. The
Catalog refreshes when something in the Yard changes, and at least every 30
seconds. It keeps your filters.

## Running on a Workstation

You need Docker with Compose and the `lvh-yard` executable. You do not need
Java or a copy of this repository.

1. Download `lvh-yard` and put it on your `PATH`, as
   [Installing the CLI](#installing-the-cli) shows.
2. Run `lvh-yard ws init`. It asks for the name of this Workstation and for its
   Base Domain. Press Enter to take the hostname of this computer as the name.
   The Base Domain has no default. It ends every hostname that this
   Workstation routes. Give a wildcard domain that resolves every name to
   `127.0.0.1`, such as `lvh.me` or `l.lvhy.cc`. If you are offline, or your
   router blocks such answers, use `localhost`. You can give both as flags
   instead:

   ```sh
   lvh-yard ws init --workstation ws-a --base-domain lvh.me
   ```

   Without a terminal, `--base-domain` is required.

   On Linux and macOS, `ws init` writes `compose.yaml`, `.env` and
   `lvh-yard.yaml` to `~/.config/lvh-yard`. If `XDG_CONFIG_HOME` is set, it writes
   to `$XDG_CONFIG_HOME/lvh-yard`. It creates `backups/`. Consul keeps its data in a
   Docker volume, not in this folder.
   Folders have mode 700, and files have mode 600.
   `ws init`, `ws compose upgrade` and `ws set` repair these modes.
   For Windows paths and storage, see [Windows Workstation setup](#windows-workstation-setup).
   `--dir` picks another folder for any `ws` command, before or after the
   subcommand. Every platform keeps the
   [Backups folder](#the-backups-folder-and-the-safety-backup) at `backups/`.

   On Linux and macOS, settings, generated options and certificates may use
   relative symbolic links whose targets stay inside the Workstation Folder.
   Windows requires real files and folders, with no links of any kind.
   Backup input and output paths, and the TLS import flags `--ca`, `--cert`
   and `--key`, may name files elsewhere.

   This restriction covers the CLI's own file operations. Docker Compose,
   your editor and `scp` or `ssh` still run with your normal permissions.

   `ws init` makes a new Gossip Key and stores it in `.env`. It does not print
   the key. This Workstation then starts a Yard of its own, and on Linux and
   macOS Consul listens on `127.0.0.1` only, so no other computer reaches its
   ports. See [Joining a Yard](#joining-a-yard).

3. Start this Workstation: `lvh-yard ws compose start`.
   This starts Consul, the dashboard and Traefik. You can also run
   `docker compose up -d` in the Workstation Folder.
4. Check that LVH Yard works: `lvh-yard status`.
5. Open [http://127.0.0.1:7780](http://127.0.0.1:7780) or
   [http://yard.lvh.me](http://yard.lvh.me).

To invite another Workstation or join another Yard, see
[Joining a Yard](#joining-a-yard).

On its first start, the dashboard sets up three Services with Local Scope:

- `yard`, the dashboard, at [yard.lvh.me](http://yard.lvh.me)
- `consul-ui`, the web page of Consul, at
  [consul-ui.lvh.me](http://consul-ui.lvh.me)
- `traefik-admin`, the admin page of Traefik, at
  [traefik-admin.lvh.me](http://traefik-admin.lvh.me)

The dashboard does this setup only once. After that, they are ordinary
Services. You can change, unpublish or delete them, and they do not come back.
The dashboard is always at `127.0.0.1:7780` too, also when Traefik is down or
`yard` is unpublished.

From any directory, `lvh-yard ws compose start`, `ws compose stop`,
`ws compose restart` and `ws compose recreate` manage this Workstation's
services. `recreate` runs `docker compose up -d --force-recreate`, which applies
changed settings. Each command prints the `docker compose` line it runs, with the Workstation Folder as the project
directory. The commands work while the dashboard is down and return the exit
code of `docker compose`. Docker with Compose must be on your `PATH`.

`lvh-yard ws compose down` removes the containers and the network of this
Workstation. Consul keeps its data in the Docker volume `<project>_consul-data`,
so a later `ws compose start` finds Services, Routes and the other setup as they
were. Add `-v` (`--volumes`) to remove the volumes too. Then Consul starts empty.
Export a [Backup](#backups) first. `-v` also removes any volume that you added to
`compose.yaml`. It leaves the files in the Workstation Folder, `backups/` too.

`lvh-yard ws compose logs` shows all service logs. Give `consul`, `traefik` or
`dashboard` to show one service, and add `-f` to follow its logs. For example,
`lvh-yard ws compose logs consul -f` follows Consul's logs. Consul logs only errors.
Use `--dir <folder>` with any of these commands to select another Workstation
Folder. You can also run `docker compose up -d`, `stop`, `restart` or `logs`
directly in that folder.

`lvh-yard ws compose logs traefik -f` shows one line for each request that Traefik
sends on. The line ends with the Route, the address of the Instance that
answered, and the time in milliseconds. The status of the response comes after
the request. Traefik logs only the Services that you turn on, for example with
`lvh-yard publish <service> --access-log on`.

If a request to the dashboard fails, `lvh-yard ws compose logs dashboard` shows one
line for it, with no stack trace. To also log the stack trace, set
`YARD_STACK_TRACES=true` in `.env`. Then run `lvh-yard ws compose recreate`.

If a page of the dashboard fails, the browser shows an error page. It names the
error, says what failed, and links to the Status page. A failed page that you
can load again has a "Try again" button. A page that does not exist links to
the Catalog. The dashboard talks to Consul only through the Consul HTTP API,
whatever runs Consul. If that API does not answer, the page shows its address
and a request that tests it. The REST API still answers errors with JSON.

The files that `ws init` writes are a reference setup, and they are yours. You
can change `compose.yaml`, or run Consul, Traefik and the dashboard another
way. The comments in `compose.yaml` and `.env` explain each value. The `ws`
commands use local files and `docker compose`. Other `lvh-yard` commands talk
to the dashboard.

### Windows Workstation setup

Use Docker Desktop with Linux containers and the WSL2 backend. The tests used Docker Desktop 4.99.0.
In Docker Desktop, enable host networking and disable Enhanced Container
Isolation. The CLI does not change Docker settings, Windows Firewall or
PowerShell execution policy. See
[Docker's host-network requirements](https://docs.docker.com/engine/network/drivers/host/#docker-desktop),
read on 2026-11-14.

The Windows runtime supports IPv4 targets only.
Use IPv4 addresses for Peers. Instances may use IPv4 addresses or hostnames
with IPv4 addresses.
IPv6 targets are unsupported on Windows. Linux and macOS keep their existing
endpoint support.

Run the CLI natively in Windows PowerShell 5.1 or PowerShell 7 as an ordinary
Windows user. The default Workstation Folder is
`$env:LOCALAPPDATA\lvh-yard\workstation`. `--dir` or `YARD_CONFIG_DIR` can select
another folder, including paths with spaces, apostrophes or Unicode.
The executable folder, `$env:LOCALAPPDATA\lvh-yard\bin`, stays separate.
Use local NTFS storage. Network shares, mapped network drives and cloud-synced
folders, such as OneDrive, are unsupported even when a write succeeds.

The CLI makes configuration, certificates and `backups/` private to your
Windows account. The privacy boundary covers other ordinary local users.
It does not protect against administrators or anyone who controls Docker.
Keep exported Backups private too: they can contain basic-auth hashes.
The [certificate roles](#workstation-ca-and-certificates) stay separate.
Consul receives only `tls/consul/`, and Traefik receives only `tls/traefik/`.
Runtime containers never receive the CA private key.

1. Create the Workstation with `lvh-yard ws init`.
   Setup suggests a LAN IPv4 address when it can choose one safely.
   If several suitable adapters remain, choose the address reachable from the
   other Workstations. Without a terminal, an ambiguous address needs
   `--address`, for example:

   ```powershell
   lvh-yard ws init --workstation ws-win --address 192.168.1.20
   ```

   The advertised LAN address is separate from container listener addresses.
   For dashboard and Traefik listeners, use only `127.0.0.1` or `0.0.0.0`.
   Docker Desktop containers cannot bind a native Windows interface address.
   `0.0.0.0` exposes a listener on every address; it is not a private default.
   Consul HTTP and the Traefik admin listener stay on loopback.

2. From the Workstation Folder, start the generated setup directly:

   ```powershell
   Set-Location -LiteralPath "$env:LOCALAPPDATA\lvh-yard\workstation"
   docker compose config
   docker compose up -d
   ```

   If you selected another folder, use that path instead.
   Compose needs no hidden overlay or CLI-only environment preparation.
   Its one-shot volume setup gives Consul access to its data.
   The long-lived Consul and dashboard processes run non-root.
   The `scripts/traefik.sh` script copies stable configuration
   and Traefik certificate snapshots into Linux-local storage.
   It touches unchanged local configuration when certificate content changes.
   Keep custom YAML or TOML in `traefik/dynamic/` and use
   `/etc/traefik/tls/` for Traefik certificate paths.

3. Check `lvh-yard status`, then open `http://127.0.0.1:7780`.
   For LAN membership, follow [Joining a Yard](#joining-a-yard).

#### Keeping, resetting, moving and removing Windows state

Consul stores its state in a named Docker volume scoped to the Compose
project. Compose names the project after the Workstation Folder and the volume
`<project>_consul-data`. Two folders with the same name share one volume, so
give each Workstation Folder its own name.

Four actions treat this state in different ways:

| Action                                                         | Consul state | Workstation Folder                 |
| -------------------------------------------------------------- | ------------ | ---------------------------------- |
| Stop, start, recreate, image upgrade, Docker Desktop restart   | Kept         | Kept                               |
| [Reset](#reset-a-windows-workstation)                          | Deleted      | Kept                               |
| [Backup transfer](#move-a-workstation-to-another-platform)     | Not copied   | A Backup file goes into `backups/` |
| [Complete removal](#remove-all-state-of-a-windows-workstation) | Deleted      | Deleted                            |

After Docker Desktop or the computer restarts, run `lvh-yard ws compose start`.
It starts the same project and uses the same volume.
`lvh-yard ws compose upgrade` changes only image lines, so it keeps the volume
too. See [Upgrading](#upgrading).

##### Reset a Windows Workstation

A reset deletes the Consul state of one Workstation and keeps its files.
Export a Backup first. A Backup restores only [its defined contents](#backups).
Then select the Workstation Folder and check the Compose project:

```powershell
Set-Location -LiteralPath "$env:LOCALAPPDATA\lvh-yard\workstation"
lvh-yard export -o backups\before-reset.yaml
docker compose ls
docker compose config
```

**Data loss:** the second command below removes the Consul volume of the
selected project. It deletes Services, Publications, Route Profiles and other
setup held in Consul. There is no undo for state outside a Backup. Run it only
for the project you intend to erase. First stop the project. Do not add
`--volumes`: that would also remove the volumes that you added to your
`compose.yaml`.

```powershell
docker compose down
docker volume rm <project>_consul-data
```

Use the same project name and Compose files that started the Workstation.
`docker compose config` shows the project name, and `docker volume ls` shows
the volume. Never use a global volume prune as a Workstation reset.
The commands do not touch another project's volumes, and your own volumes in
this project stay too. They leave configuration, certificates, private keys and
exported Backups in the Workstation Folder. The next `lvh-yard ws compose start`
begins with an empty Consul. To get your setup back, import the Backup:

```powershell
lvh-yard ws compose start
lvh-yard import backups\before-reset.yaml
```

See [Compose's volume removal rules](https://docs.docker.com/reference/cli/docker/compose/down/),
read on 2026-11-14.

##### Move a Workstation to another platform

A move between Windows, Linux and macOS uses a fresh Workstation and a Backup.
Compose files are not portable between platforms. Do not copy `compose.yaml`,
`.env`, a Docker volume or a Consul data folder to another platform.
LVH Yard has no migration of raw Consul state.

1. On the old Workstation, run `lvh-yard export -o <file>`.
   On Windows, the file is protected from other ordinary users.
2. Copy the file to the new computer. Treat it as private: it holds the
   basic-auth hashes of your Route Profiles.
3. On the new computer, run `lvh-yard ws init` and start the Workstation.
4. Run `lvh-yard import <file>`. See [Importing a Backup](#importing-a-backup).

Only what a Backup holds arrives. A Backup holds no Bound Instances, Foreign
Instances, Health, Remote Services, certificates or private keys, and no Gossip
Key. A Backup does not archive private keys. Join the Yard again with
[`ws yard join`](#joining-a-yard) on the new Workstation, and set its `.env`
again.

##### Remove all state of a Windows Workstation

Deleting the Workstation Folder alone leaves Docker-managed state: the project's
containers, its network and its `consul-data` volume stay in Docker.
To remove all state, first remove the project, then delete the folder.
This deletes the private keys and exported Backups in the folder too:

```powershell
Set-Location -LiteralPath "$env:LOCALAPPDATA\lvh-yard\workstation"
docker compose down --volumes
Set-Location $env:USERPROFILE
Remove-Item -Recurse -Force -LiteralPath "$env:LOCALAPPDATA\lvh-yard\workstation"
```

This also removes any volume that you added to the project's `compose.yaml`.
Images stay in Docker. Remove the ones you no longer need with `docker image rm`.
If you deleted the folder first, find the project name with `docker compose ls -a`
and remove it by name:

```powershell
docker compose -p <project> down --volumes
```

### Joining a Yard from Windows

A Windows Workstation uses these addresses for different jobs:

| Setting                         | Job                                                                                      |
| ------------------------------- | ---------------------------------------------------------------------------------------- |
| `YARD_BIND_ADDRESS`             | The LAN address that Consul tells other Workstations. `ws init --address` sets it.       |
| `YARD_INSTANCE_HOST`            | The host of Instances for other Workstations. If it is empty, it is `YARD_BIND_ADDRESS`. |
| Consul listen address           | Always `0.0.0.0` inside Docker Desktop. You cannot change it.                            |
| Dashboard and Traefik listeners | `127.0.0.1` by default.                                                                  |

Docker Desktop makes only wildcard and loopback listeners of a container
visible on the Windows computer. A Consul bind to the address of the Docker
virtual machine was not reachable from the LAN. So on Windows, Consul starts
with `-bind=0.0.0.0` and listens on every adapter. `bind-address` sets only the
address that Consul advertises to other Workstations. It does not limit where
Consul listens.

Windows Firewall decides who can reach those ports, and Docker Desktop may
already allow them. On a computer that we tested, Docker's own rules for
`com.docker.backend.exe` allowed inbound connections on the Private and Public
networks. Such a rule lets any computer on those networks reach every port that
a container listens on at `0.0.0.0`, Consul's ports included. A narrow rule of
your own does not limit that. To see the rules, run this in PowerShell:

```powershell
Get-NetFirewallRule -Direction Inbound -Action Allow -Enabled True | Where-Object DisplayName -match 'docker'
```

To lower the risk:

- Set your network to Private, and only on a network that you trust.
- Every Yard connection is encrypted: `ws yard init` and `ws yard join` always turn Consul TLS on.
- Do not publish a container port on `0.0.0.0` unless you want other computers
  to reach it.

The Consul server port (TCP, default 8300) and the WAN gossip port (TCP and
UDP, default 8302) must be reachable from the other Workstations.
If no Docker rule already allows them, add rules yourself, in both directions,
for the Yard's addresses only. The CLI never changes firewall rules.
Docker Desktop also listens on the LAN gossip port (default 8301) on every
address. The Yard does not need it, so do not allow it.
Consul HTTP, DNS and gRPC, the dashboard, Traefik and the Traefik admin
listener stay on `127.0.0.1`. Joining a Yard does not change that, and you
must not open them for federation.

A Windows Workstation joins and shares with the same `ws yard` commands. `init`
and `join` set `YARD_BIND_ADDRESS`, which on Windows is the address that Consul
advertises. The
Windows CLI never copies certificates over ssh: the zip file travels by email or
any other way, and the other side reads it with `ws yard join`. On Windows,
unzipping is not needed, but the file explorer cannot open the zip file either:
use 7-Zip, WinZip or Keka to read it. `share`, `join` and `standalone` were tested
between a Windows 11 Workstation and a Linux one, in both directions, with Consul
TLS. They did not run on macOS.

Address selection has these limits:

- Setup suggests the address of the adapter that holds the default route. It
  skips adapters that are down, loopback and link-local addresses, and adapters
  whose name contains `vEthernet`, `WSL` or `docker`. It suggests nothing when
  that adapter looks like a VPN or another tunnel: its name or description
  has `vpn`, `tap`, `tun`, `wireguard`, `wintun`, `ppp`, `tailscale` or
  `zerotier`, or it is a point-to-point adapter. The network card of a virtual
  machine, such as a Hyper-V, VMware or VirtualBox guest, counts as a normal
  adapter. Then setup asks you to choose, or needs `--address` when no
  terminal is available.
- `--address` takes any specific IPv4 address that is not loopback,
  link-local or `0.0.0.0`. It does not check which adapter has the address or
  whether other Workstations reach it. Use the address of your LAN adapter.
  A WSL, VPN or other VM-only address is not reachable. Other Workstations
  also treat an address from 172.17.0.0 to 172.31.255.255 as Out-of-range and
  give its Instances no Route.
- Names such as `host.docker.internal` are refused. An address must be an IPv4
  address.
- A Static Instance without `--host` uses `YARD_INSTANCE_HOST`, which is
  `YARD_BIND_ADDRESS` unless you set it. It must listen on that address or on
  every IPv4 address. A process that listens only on `127.0.0.1` works for
  Local Scope only.

### Dashboard settings

`lvh-yard.yaml` holds structured dashboard settings in the Workstation Folder.
Every line starts as a comment and shows the dashboard's defaults. The file
is readable only by its owner. The dashboard container reads it through a
read-only mount.

To change a setting, uncomment its parent sections and its value. Keep the
`YARD_*` placeholders so that `.env` values still win. For example:

```yaml
lvh-yard:
  events:
    debounce-window: ${YARD_EVENTS_DEBOUNCE_WINDOW:500ms}
```

After saving the file, run `docker compose restart dashboard` in the Workstation
Folder. `lvh-yard ws compose upgrade` and `lvh-yard ws set` leave the file unchanged.
A dashboard run from the sources loads the Workstation Folder's settings through
the [development tasks](development.md#a-yard-on-one-computer).

### Joining a Yard

A Workstation starts alone: Consul listens on `127.0.0.1`, so no other computer
reaches its ports. Four commands change that. The Gossip Key encrypts the
gossip between the Workstations, and Consul TLS checks every Workstation by its
certificate, so every Yard connection is encrypted.

| Command            | Runs on                              | Does                                           |
| ------------------ | ------------------------------------ | ---------------------------------------------- |
| `ws tls ca create` | The Workstation that starts the Yard | Makes its CA, once                             |
| `ws yard init`     | The same Workstation                 | Opens it to the Yard, once                     |
| `ws yard share`    | The same Workstation                 | Writes an invitation, once for each new member |
| `ws yard join`     | Each joining Workstation             | Reads the invitation                           |

**Init.** On the Workstation that starts the Yard, make a CA, then open the
Workstation:

```sh
lvh-yard ws tls ca create
lvh-yard ws yard init
```

`ws yard init` needs the CA. Without one, it stops and tells you to run
`ws tls ca create`, then `ws yard init` again. With a CA, it does this:

1. Signs the Consul certificate of this Workstation with the CA.
2. Installs it in `tls/consul/` and turns Consul TLS on.
3. Sets the LAN address that Consul listens on.

Then it prints the `compose recreate` command: run it to apply the change.
It also prints the ports to allow in the firewall. `--address <ip>` chooses the
LAN address and `--days` the validity of the certificate. Without `--address`,
the command uses a specific non-loopback Consul bind address first, then this
computer's default-route IPv4 address, then a single active IPv4 interface
address, and it stops when it finds none. Run it again and it says "Already
open" and changes nothing.

**Share.** For each Workstation that joins, run on the same Workstation:

```sh
lvh-yard ws yard share ws-b
```

`ws-b` is the name of the joining Workstation, and it must not already be in the
Yard. `share` stops and tells you to run `ws yard init` first if you did not.
The command signs a Consul certificate for `ws-b` with this Workstation's CA,
and writes one zip file, `certificates/ws-b-yard.zip`. The zip file is encrypted
with AES-256, and holds the Gossip Key, this Workstation's CA certificate, the
certificate and key of `ws-b`, the address of this Workstation, and a
`README.txt`. It asks for a password, or makes one and prints it once. Send the
zip file and its password by two different ways, such as an email and a message.
`--days` sets the validity of the certificates and `--output <file>` the zip
file. `share` changes nothing else on this Workstation.

Only the Workstation that holds the CA key can share. A Workstation that joined
has only the public CA certificate, so it cannot invite a third one: share from
the Workstation that made the CA.

**Join.** On the joining Workstation, made with `lvh-yard ws init` under the
name that you gave to `share`, run:

```sh
lvh-yard ws yard join ws-b-yard.zip
```

It asks for the password of the zip file without showing it, or takes
`--password`. It refuses a zip file made for another name, or a Workstation that
already trusts the CA of another Yard. Run `ws yard standalone` first for that.
It installs the Consul certificate and key in `tls/consul/`, turns Consul TLS
on, and sets the Gossip Key, the Peer and the LAN address of Consul.
`--address <ip>` chooses that LAN address. Each Workstation can choose its own
Base Domain. Then run the printed `compose recreate` command and check
`lvh-yard status`. A first start can use `lvh-yard ws compose start`.

**Standalone.** To leave a Yard, run `lvh-yard ws yard standalone`. It makes a
new Gossip Key, so the other Workstations can no longer talk to this one,
clears the Peers, turns Consul TLS off, deletes the certificate and key in
`tls/consul/`, and makes Consul listen on `127.0.0.1` again. It keeps the CA,
what the CA signed, the Traefik certificate and the Consul data. On native
Windows, Consul keeps its LAN address. Then run the printed `compose recreate`
command. The other Workstations still list this one until their Consul
servers notice that it is gone.

Keep the zip file and its password private. Delete the zip file when the other
Workstation has joined. After a Workstation joins, check the Peers on the one
that shared, and save the ones that answer in `YARD_PEERS`:

```sh
lvh-yard ws yard peers
lvh-yard ws yard peers --sync
```

Before joining, manually allow traffic between the Workstations in each
computer's firewall. With the default ports, allow server RPC on TCP 8300 and
WAN gossip on TCP and UDP 8302, in both directions. If you changed the ports,
use the values that `share` and `join` print.
Keep these rules scoped to the Yard's computers and the intended network.
Do not expose the Consul HTTP API or Traefik admin listener for federation.
See [Consul's ports reference](https://developer.hashicorp.com/consul/docs/reference/architecture/ports),
read on 2026-11-14. For Windows firewall access and address limits, see
[Joining a Yard from Windows](#joining-a-yard-from-windows).

**By hand.** No other command sets Consul TLS or the Gossip Key. For a setup
that `share` and `join` do not make, such as a company CA for Consul, edit the
files yourself: put the CA certificate, the certificate and the key in
`tls/consul/`, add your Consul options in `consul.d/`, and set
`YARD_GOSSIP_KEY`, `YARD_PEERS` and `YARD_BIND_ADDRESS` in `.env`. Every
Workstation of the Yard must use the same Consul TLS mode. Use `lvh-yard ws set
peers` for the Peers. See
[Workstation CA and certificates](#workstation-ca-and-certificates) for the
Consul certificate rules.

### Listing Peers

To see the other Workstations that this Workstation's Consul sees, run:

```sh
lvh-yard ws yard peers
```

It asks the local Consul, so Consul must run. The list leaves out a
Workstation that does not answer and names it on a last line. To replace
`YARD_PEERS` with the Peers on the list, run:

```sh
lvh-yard ws yard peers --sync
```

`--sync` leaves `.env` as it is when no other Workstation answers. Like `ws set`,
it does not restart services: run the printed `docker compose` command.

### Changing settings

`lvh-yard ws show` prints the active settings in `.env`, with the Gossip Key hidden.
`lvh-yard ws set <name> <value>` checks a value and changes one setting. It keeps
the comments. Neither `ws set` nor `ws yard join` restarts services. After either
command, run the printed command, `lvh-yard ws compose recreate`, to recreate the containers.
`ws compose restart` restarts existing containers without applying changed environment
variables. When Traefik is off, the printed commands stop its existing container
before they recreate the enabled services.

```sh
lvh-yard ws set base-domain example.test
lvh-yard ws set peers "192.168.1.10,192.168.1.11:8302"
lvh-yard ws set dashboard-bind-address 0.0.0.0
```

The setting names are `base-domain`, `peers`, `bind-address`, `instance-host`,
`traefik-bind-address`, `dashboard-bind-address`, `http-port`,
`traefik-admin-port`, `dashboard-port`, `consul-http-port`, `consul-dns-port`,
`consul-grpc-tls-port`, `consul-server-port`, `consul-serf-lan-port`,
`consul-serf-wan-port`, `out-of-range`, `stack-traces` and `traefik`.
Addresses use IPv4. Ports run from 1 to 65535. `out-of-range` takes
comma-separated CIDR ranges or an empty value. `stack-traces` takes `true` or `false`.
`traefik` takes `on` or `off`.
`instance-host` is for Windows only. It takes an IPv4 address, or an empty value
to use `bind-address`. It refuses `0.0.0.0` and multicast addresses, because no
connection can reach them. `127.0.0.1` is allowed. It works for Local Scope only.
On Linux and macOS, `ws init` sets `bind-address` to `127.0.0.1`, so Consul listens on this
computer only until `ws yard init` or `ws yard join` sets the LAN address. An
empty `bind-address` lets Consul choose the address of the default route.
For Windows LAN identity and listener limits, see
[Windows Workstation setup](#windows-workstation-setup).

To change the Workstation name, export a Backup, run `lvh-yard ws init`
under the new name, and import the Backup. There is no `ws rename`.
`ws yard standalone` makes a new Gossip Key and leaves the Yard.
Every `ws` command accepts `--dir <folder>`.

### Workstation CA and certificates

A Workstation can make its own CA with `ws tls ca create`. The CA signs the
certificates that this Workstation needs. Keep its key on this Workstation. The
key can sign certificates for anyone who trusts the CA, so never copy it to
another Workstation or a container.

The CA that `ws tls ca create` makes is for testing. Its key is a file
on one computer, and nothing can revoke a certificate it signed. Every command
under `ws tls ca`, `ws tls https create` and `ws tls mtls create` prints a warning to say so. For more than testing, use a
more robust CA, such as a company CA or a public CA, and import its
certificates with `ws tls https import`, `ws tls client import` or `ws tls mtls import`.
A more complex CA setup, such as an intermediate CA, is made by hand and imported the same way.

Each Workstation has a Consul certificate and the TLS of Traefik, with separate
keys. Consul and Traefik use the CA in different ways:

|                             | Consul                                             | Traefik                                            |
| --------------------------- | -------------------------------------------------- | -------------------------------------------------- |
| Needs                       | The same CA on every Workstation of the Yard       | Its own CA on this Workstation                     |
| Names                       | `server.<name>.consul`, `localhost`, `127.0.0.1`   | `*.<Base Domain>`, `localhost`, `127.0.0.1`, more  |
| Signed by                   | The main CA                                        | This Workstation's CA, a company CA or a public CA |
| Made and installed with     | `ws yard init`, `ws yard share` and `ws yard join` | `ws tls https`, `ws tls client` and `ws tls mtls`  |
| Sent to another Workstation | Yes, in the invitation of `ws yard share`          | Never: its key stays here                          |

For a Yard, [`ws yard init`, `ws yard share` and `ws yard join`](#joining-a-yard)
do the whole Consul part: the CA of the Workstation that starts the Yard is the
main CA, and they sign, pack, install and turn on the Consul certificate of every
Workstation. No other command handles a Consul certificate. The rest of this
section explains the CA, `ws tls ca issue`, and the three TLS parts of Traefik.

Consul needs the same CA on every Workstation of a Yard. Choose the CA of one
Workstation as the main CA of the Yard. It does not matter which one. Its
Workstation signs the Consul certificate of every Workstation, including its
own. Traefik needs no sharing: each Workstation signs its own certificates and the
Client Certificates of its own people, so those keys never leave it.

Compose mounts `tls/consul/` only in Consul and `tls/traefik/` only in Traefik.
So a Traefik process that faces browsers never holds the key that identifies
this Workstation to the other Consul servers. The Consul certificate also never
changes when a Workstation joins the Yard, because Consul checks only its own
name. The HTTPS certificate changes with the Routes that it covers.

Traefik has three TLS parts, and a Yard can use any of them without the others:
HTTPS for browsers, the check of the Client Certificates of people, and mutual TLS
to the Services. They are not Workstation Certificates. They are explained
[below](#the-three-tls-parts-of-traefik).

Every command that signs sits under `ws tls ca`, because signing needs the CA key.
Like `ws init`, each one asks for what you did not give as a flag. Without a
terminal, it uses the defaults.

`lvh-yard ws tls ca create` writes `ca/ca.crt` and `ca/ca.key` in the
Workstation Folder. The CA uses ECDSA P-256 and lasts 3650 days. Its key has
mode 600 on Linux and macOS. The reference Compose setup never mounts `ca/`.
The command refuses to replace either CA file unless you give `--force`.
Replacing the CA does not update certificates that it already signed.

What the CA signs sits in one directory for each group, next to the CA files:

```text
ca/
  ca.crt, ca.key
  yard/<workstation>/    consul.crt|key, https.crt|key, mtls.crt|key
                         for this Workstation and the others in the Yard
  service/<name>/        service.crt|key, service-fullchain.crt
  client/<name>.p12      the Client Certificates of people
```

`ws compose upgrade` moves the files of an older folder from `ca/certs/` and
`ca/clients/` to these places.

A Workstation has one CA. For another one, use another Workstation Folder, or
bring a CA with `ws tls https import`, `ws tls client import` or `ws tls mtls import`.

It asks for the common name of the CA certificate, with `LVH Yard CA - <workstation>`
as the default. Give `--name <name>` to set it without a question. The name has
1 to 64 characters. It then asks for a password for `ca.key`. The default is
none. With a password, `ca.key` is an encrypted PKCS#8 file (PBKDF2-HMAC-SHA256
and AES-256-CBC) that OpenSSL reads too. Give `--ca-key-password <password>` to
set it without a question, or `--ca-key-password ''` for none. Nothing can
recover a lost password. The question shows nothing while you type, and asks
a second time to check the password. An empty answer asks no second time.

Every command that signs needs the key: `ws tls ca issue user`, `ws tls ca issue
service`, `ws tls https create`, `ws tls mtls create` and `ws yard share`. When the
key has a password, each asks for it, or takes `--ca-key-password <password>`.
Without a terminal and without a flag, it fails. `ws tls client create` needs
only `ca.crt`, so it never asks for it.

A password on the command line shows in the process list of the computer. To
keep it out, give `--ca-key-password-file <file>` instead, to `ca create` and to
each `ca issue` command. The flag reads the password from the file, or from the
standard input for `-`, so a password manager can pipe it in:

```sh
pass show lvh-yard/ca | lvh-yard ws tls ca create --ca-key-password-file -
pass show lvh-yard/ca | lvh-yard ws tls ca issue user alice --ca-key-password-file -
```

The password is the content without one final line break. An empty file is an
error. On Linux and macOS, the command refuses a file that other users can read:
run `chmod 600` on it. Use only one of the two flags. Standard input is then not
free for questions, so pass the other answers as flags.

`lvh-yard ws tls ca issue service <name>` signs a certificate for a Service, such
as `grafana`. The Service serves HTTPS with it, and Traefik checks the Service
with the CA when the Service has mutual TLS. It asks for extra addresses, the validity and a
password for a zip file. The files are `ca/service/<name>/service.crt|key`, and
`service-fullchain.crt`, which holds the certificate followed by the CA certificate.
The zip file is `certificates/<name>-service.zip`. It holds those three files, `ca.crt`
and a `README.txt`, and it is encrypted with AES-256. Give `--password <password>` to
set the password without a question. With an empty answer, the command makes a
random one and prints it once. Send the password by another way than the zip file.
The certificate names the Service,
`localhost` and `127.0.0.1`, and each `--address`. Traefik checks only that it
names the Service, whatever the address of the Instance is. `--address` is for other
programs that connect to the Service. The certificate allows server and client use. Signing does not
copy anything to `tls/`, start TLS or change running services. A certificate uses
ECDSA P-256 and lasts 365 days. On Linux and macOS, private keys have mode 600.
`ws yard share` signs the Consul certificates itself, with the same CA, and puts
the one of the joining Workstation in its encrypted invitation,
`certificates/<workstation>-yard.zip`. No command signs a Consul certificate by hand.

`lvh-yard ws tls ca issue user <name>` signs a Client Certificate for a person,
in a `.p12` file in `ca/client/`. The parts of Traefik sign their own
certificates: `ws tls https create` and `ws tls mtls create` call the Workstation
CA, as `ca issue` does. `ws tls ca issue traefik` no longer exists.

A certificate must expire before or with its CA. All these commands accept
`--dir <folder>` and repair the Workstation Folder's private permissions.

#### The three TLS parts of Traefik

Each part has its own files in `tls/traefik/`, its own generated file in
`traefik/dynamic/`, and its own setting in `.env`. So you turn each one on and off
alone, and turning a part off keeps its files.

|                         | HTTPS                                                        | Client Certificate authentication                                       | Mutual TLS to Services                                                         |
| ----------------------- | ------------------------------------------------------------ | ----------------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| Group                   | `ws tls https`                                               | `ws tls client`                                                         | `ws tls mtls`                                                                  |
| Proves                  | The Workstation's hostnames, to the browser                  | A person, to Traefik                                                    | Traefik, to a Service                                                          |
| Presented by            | Traefik                                                      | The browser                                                             | Traefik                                                                        |
| Checked by              | The browser, with the CA that it trusts                      | Traefik, with `client-ca.crt`                                           | The Service, with its CA. Traefik checks the Service with `mtls-ca.crt`        |
| Signed by               | This Workstation's CA, a company CA or a public CA           | This Workstation's CA, or the CA that you import                        | This Workstation's CA, or a CA that you import                                 |
| Needed for              | HTTPS on any Route                                           | Only a Route whose Route Profile has `clientCertificate: {}`            | Only a Service with the setting Mutual TLS to the Instances                    |
| Files in `tls/traefik/` | `https.crt`, `https.key`                                     | `client-ca.crt`                                                         | `mtls.crt`, `mtls.key`, `mtls-ca.crt`                                          |
| Generated file          | `10-tls.yaml`                                                | `11-client-auth.yaml`                                                   | None. The dashboard gives each such Service its own transport                  |
| On when                 | `YARD_HTTPS_PORT` has a value                                | `YARD_CLIENT_AUTH_ENABLED=true`                                         | `YARD_MTLS_ENABLED=true`                                                       |
| The other side needs    | To trust this Workstation's CA, unless a public CA signed it | A `.p12` file from `ws tls ca issue user`, in its personal certificates | A certificate from `ws tls ca issue service`, and `ca/ca.crt` to check Traefik |

Every group has the same four commands:

- **`create`** uses this Workstation's CA, and then turns the part on.
  - `https create` signs the HTTPS certificate of this Workstation. It names
    `*.<Base Domain>`, `localhost`, `127.0.0.1` and each `--address`. It takes
    `--days`, and the CA key password flags.
  - `client create` makes Traefik trust `ca/ca.crt` for Client Certificates. It
    signs nothing, so it never asks for the CA key password. Sign each person
    with `ws tls ca issue user`.
  - `mtls create` signs a client certificate for Traefik, which allows client use
    only. It also makes `ca/ca.crt` the CA that checks the Services.
- **`import`** uses files that you bring, instead of this Workstation's CA, and
  then turns the part on. It runs its checks before it writes any file.
  - `https import --cert <file> --key <file> [--ca <file>]` needs a certificate
    that names `yard.<Base Domain>`, and that `--ca` or a public CA signed. Put
    the whole chain in the `--cert` file.
  - `client import --ca <file>` takes only the CA certificate. The file may hold
    more than one CA.
  - `mtls import --ca <file> --cert <file> --key <file>` takes the certificate that
    Traefik shows to Services, and the CA that checks the Services. The two can
    come from different CAs.
- **`enable`** checks the files in `tls/traefik/`, writes the generated file and
  turns the part on. It needs the files from `create` or `import`.
- **`disable`** removes the generated file and turns the part off. It keeps the
  files.

All commands that turn a part on or off save `.env`. Apply the change with the printed
`lvh-yard ws compose recreate`. A part needs Traefik on: they refuse to run while
`ws set traefik off` is in effect, except `disable`. `ws compose upgrade` writes the
generated files of the parts that are on again, and keeps the certificates.

`ws tls install`, `ws tls enable` and `ws tls disable` no longer exist. A folder
from before the three parts has `tls/traefik/traefik.crt|key` and `ca.crt`. The
upgrade renames them to `https.crt|key` and `client-ca.crt`, and keeps the check
of Client Certificates on, because HTTPS used to turn it on.

##### HTTPS

The default `*.<Base Domain>` covers local Routes only. A wildcard matches one
label, so `*.lvh.me` does not cover `app.ws-b.lvh.me`. A DNS wildcard must be the
whole leftmost label, such as `*.ws-b.lvh.me`. Wildcards cannot cover IP
addresses. For each remote Workstation whose Routes this Workstation serves, add
an extra wildcard. For example, on a Workstation with Base Domain `lvh.me`:

```sh
lvh-yard ws tls https create --address '*.ws-b.lvh.me'
```

This covers `app.ws-b.lvh.me`, but not `app.ws-c.lvh.me` or
`deeper.app.ws-b.lvh.me`. Repeat `--address` for each remote Workstation. When a
new Peer needs Route coverage, run `https create` again with all the `--address`
values. The Consul certificates do not change. HTTPS keeps a port that you set with
`ws set https-port`, and uses 443 when none is set.

##### Client Certificate authentication

Traefik asks for a Client Certificate only on Routes whose Route Profile has
`clientCertificate: {}`, and only on HTTPS. Such a Route has no hostname while
HTTPS or this part is off. A Client Certificate works on the Routes of the
Workstation whose CA signed it, because each Traefik checks with its own
`client-ca.crt`. A Route Profile with `clientCertificate: {}` on a Publication that
another Workstation routes asks the Traefik of that Workstation. To serve it there,
sign the person a certificate on that Workstation, or run `ws tls client import
--ca ca.crt` there, with the CA that signed it.

##### Mutual TLS to Services

Traefik calls the Instances of a Service over HTTPS, with its certificate, only for
a Service that has the setting **Mutual TLS to the Instances**. Other Routes keep
plain HTTP, so a Service that has no TLS keeps working. A Service with the setting
has no Route, and a Service Warning, while this part is off.

While HTTPS is on, a Route without the setting sends the traffic to its
Instances over plain HTTP. If an Instance is not on loopback, the Service has a
Service Warning of level **risk**, because anyone on that network can read the
traffic. An Instance on loopback gets no warning, because its traffic does not
leave the computer.

1. Run `ws tls mtls create`.
2. Run `ws tls ca issue service <name>`, and give the
   Service `service-fullchain.crt` and `service.key`. The Service must trust `ca/ca.crt` and
   ask for a client certificate.
3. Turn on the setting of the Service. Use the Mutual TLS switch on the Service
   page, or run:

   ```sh
   lvh-yard publish <name> --mutual-tls on
   ```

Traefik checks each Instance's certificate in two ways. The CA of the certificate must
be `ca/ca.crt` (`mtls-ca.crt`), and the certificate must name the Service. The dashboard
gives each such Service its own Traefik transport, with the Service name as the
name to check. So a certificate for another Service is refused, whatever the address
of the Instance is, and `--address` is not needed for Traefik. The same rule applies to
a Remote Service that your Workstation routes: its Instances need a certificate for the
name of that Service. When an Alias gives the Instances, their certificate must name the
Service that has the Route.

If the certificate does not match, Traefik answers 500 and the Reachability test fails
with a reason. A certificate that you import from another CA must name the Service too.

The dashboard does not check that an Instance speaks TLS, and its Reachability
test of an Instance is a plain connection.

##### Consul

Only `ws yard init` and `ws yard join` install a Consul certificate and turn
Consul TLS on. Turn Consul TLS on everywhere in the Yard in one go. A Workstation
without it cannot federate with one that requires it. Consul verifies
certificates in both directions for RPC. Its local HTTP API stays plain HTTP.
If your `compose.yaml` still mounts `./tls` in Consul and Traefik, `ws compose
upgrade` says so: mount `./tls/consul` in Consul and `./tls/traefik` in Traefik,
and install the certificates again.

#### Browser trust

The CLI does not change browser trust. In your browser's certificate settings,
import `ca/ca.crt` as a trusted certificate authority for websites. Firefox
has an Authorities tab in its certificate manager. Browsers that use the
operating system's trust store need the CA certificate installed there instead.
Use your browser or operating system's instructions for that step.

Import only `ca.crt`, never `ca.key` or a Workstation's private key.
Trusting the CA lets the browser trust any website certificate it signs.
To remove that trust, remove the CA from the same certificate store.

On Windows, import the CA into your user's certificate store, without
administrator rights. The tests ran no browser for this step:

```powershell
$ws = "$env:LOCALAPPDATA\lvh-yard\workstation"   # or the folder you chose
Import-Certificate -FilePath "$ws\ca\ca.crt" -CertStoreLocation Cert:\CurrentUser\Root
```

The command needs a desktop session. Over SSH it fails with
`UI is not allowed in this operation`.
Firefox has its own store: use its Authorities tab. To remove the trust, delete
the certificate from the same store. To check a certificate without changing
trust, name the file for one request:
`curl.exe --ssl-no-revoke --cacert "$ws\ca\ca.crt" https://app.lvh.me/`.

#### Client Certificates

Turn on HTTPS and the check of Client Certificates first, with
`lvh-yard ws tls https create` and `lvh-yard ws tls client create`.

On the Workstation that serves the Routes, run
`lvh-yard ws tls ca issue user alice`. It asks for the username or email, a password and
the validity, and writes `ca/client/alice.p12` in the Workstation Folder with
private permissions.
The file holds Alice's private key, Client Certificate and the CA certificate.
If you leave the password empty, the command makes a random one and prints it
once. Save it before closing the terminal. To choose a password, answer the
question or add `--password <password>`. The question shows nothing while you
type, and asks a second time to check the password. The command does not print
a password that you give.

A Client Certificate uses ECDSA P-256 and lasts 365 days. It allows client
authentication only. Use `--days <number>` to change its validity, within
the CA's validity. Use `--dir <folder>` for another Workstation Folder.

In Firefox, open Settings, Privacy & Security, Certificates, View Certificates,
then Your Certificates. Choose Import, select `alice.p12` and enter its
password. In Chromium-based browsers, open the certificate manager from
Settings and import the file into personal certificates. The operating
system may manage this store. Follow its import instructions if so.
When a Route asks for a Client Certificate, select Alice's certificate.
Import this Workstation's CA for website trust as described above, too.
Share the `.p12` file and its password only with the person it identifies.

On Windows, `ca/client/alice.p12` stays private like the other secrets: other
Windows users cannot read it. A copy in another folder gets that folder's
access rights, so import the file and delete the copy. To import it for your
user, run:

```powershell
$password = Read-Host -AsSecureString 'Password of alice.p12'
Import-PfxCertificate -FilePath "$ws\ca\client\alice.p12" -CertStoreLocation Cert:\CurrentUser\My -Password $password
```

Firefox needs its own import, as above. The tests did not use a browser for
this step. To check the import without a browser, give `curl.exe` the thumbprint that
`Import-PfxCertificate` printed:
`curl.exe --ssl-no-revoke --cacert "$ws\ca\ca.crt" --cert "CurrentUser\MY\<thumbprint>" https://admin.lvh.me/`.
Remove it with `Remove-Item Cert:\CurrentUser\My\<thumbprint>`.

#### Sending the user to the Service

A Service can read who the person is from the Client Certificate. Chain the
certificate rule with the Traefik middleware `passTLSClientCert`, which adds a
header to each request. Save this as `profiles.yaml`:

```yaml
http:
  middlewares:
    certificate:
      clientCertificate: {}
    cert-info:
      passTLSClientCert:
        info:
          subject:
            commonName: true
          serialNumber: true
          notAfter: true
    secure-with-user:
      chain:
        middlewares:
          - certificate
          - cert-info
```

Run `lvh-yard profiles set profiles.yaml`, then `lvh-yard publish <service>
--profile secure-with-user`. The **Client Certificate user** example of the
Route Profiles page shows the second profile.

The Service gets the header `X-Forwarded-Tls-Client-Cert-Info`, URL-encoded. After
decoding, it holds `Subject="CN=alice";Issuer="...";SerialNumber="...";NA="..."`.
`CN` is the name that you gave to `ws tls ca issue user`, so use an email as the
name if the Service needs one. The value can hold the CA certificate after the
person's certificate, when the client sends the whole chain, as `curl` does with a
`.p12` file. Read the first entry. Add `pem: true` to also get the whole
certificate in `X-Forwarded-Tls-Client-Cert`.

The Service must trust the header only on connections from Traefik. A connection
straight to the Instance's address and port skips Traefik, so a client can send any
header there.

#### Sending certificates

Only two commands pack certificates in a zip file: `ws tls ca issue service` and
`ws yard share`, which is encrypted. Send everything else by hand:

- **A user:** send `ca/client/<name>.p12` and `ca/ca.crt`. The recipient imports
  the `.p12` file as in [Client Certificates](#client-certificates), and `ca.crt`
  as a certificate authority to trust the HTTPS Routes.
- **The password of the `.p12` file:** send it by another way than the file, such
  as a phone call or a chat message. Then one stolen email cannot open the key.
- **Never send:** `ca/ca.key`, or a key from `tls/`.

Delete the sent copies when the recipient has imported the files.

### A host without Traefik

On a shared host with no direct users, turn Traefik off:

```sh
lvh-yard ws compose stop
lvh-yard ws set traefik off
lvh-yard ws compose start
```

The command sets `YARD_TRAEFIK_ENABLED=false` in `.env` and removes only
`traefik` from `COMPOSE_PROFILES`. Consul and the dashboard still run and
publish Instances. Other Workstations can route to those Instances through
their own Traefik.

Status has no Traefik Status Part. Reachability still tests Instances directly,
and says that this Workstation has no Traefik for a test through a Route.
On the first setup, the dashboard registers `yard` and `consul-ui`, but skips
`traefik-admin`. It keeps the setup marker, so turning Traefik on later does
not register `traefik-admin`.

If the Service already exists, remove it with `lvh-yard remove traefik-admin`.
To turn Traefik back on, stop the Workstation, run
`lvh-yard ws set traefik on`, then run `lvh-yard ws compose start`.
A dashboard run from the sources reads the same
`YARD_TRAEFIK_ENABLED` setting.

### HTTPS Routes

HTTPS is off by default. When Traefik is on, installing the Traefik certificate with
`lvh-yard ws tls install` (or running `lvh-yard ws tls enable`) sets
`YARD_HTTPS_PORT=443` if it is empty and writes the Traefik TLS file.
Apply the settings with `lvh-yard ws compose recreate`. Every Route then answers on HTTP and HTTPS. HTTP does not
redirect to HTTPS. The dashboard then shows two links for each hostname: the hostname for HTTP, and a lock icon for
HTTPS. If this Workstation's CA signed the Traefik certificate, import `tls/traefik/ca.crt`
into your browser's trusted certificate authorities to trust it. A public
certificate needs nothing.

Use `lvh-yard ws set https-port 8443` to choose another port, or
`lvh-yard ws set https-port ''` to turn HTTPS off. The HTTPS entry point uses
`traefik-bind-address`. The dashboard reads `YARD_HTTPS_PORT` in Compose and
when run from the sources. With Traefik off, `ws tls install` and `ws tls enable`
refuse to run, and no Traefik TLS configuration is written.

The generated `traefik/dynamic/10-tls.yaml` defines the default TLS option and
the fixed option `yard-client-certificate@file`. The latter requires a Client
Certificate signed by the CA of this Workstation. The default option does not require one.

### Upgrading

`lvh-yard update` installs the latest release from GitHub and upgrades this
Workstation. It checks the download against `SHA256SUMS`. Before it changes
anything, it asks the dashboard for a [Backup](#backups) and saves it in the
Backups folder as `<workstation>-<yyyyMMdd-HHmmss>-before-update.yaml`. If the
dashboard is stopped, it warns that there is no Backup and goes on, because the
Consul data stays in its Docker volume. To get a Backup first, run
`lvh-yard ws compose start` and then `lvh-yard update`. Then `update` replaces
the executable that you ran. Then the new executable runs `ws compose upgrade`,
because only the new release has the files of the new release. On a terminal, it
asks whether to recreate the services. Without a terminal, it prints the command.

```sh
lvh-yard update --check          # shows the latest release, changes nothing
lvh-yard update                  # installs the latest release
lvh-yard update 0.4.0            # installs this release, an older one too
lvh-yard update --dir <folder>   # upgrades another Workstation Folder
```

`update` upgrades one Workstation Folder. Run `ws --dir <folder> compose upgrade`
for each other one. A development build cannot update. If you cannot write to
the folder of the executable, run the install script again.

`lvh-yard ws init` does not overwrite a file unless you give `--force`.
After you install a new `lvh-yard` by hand, run `lvh-yard ws compose upgrade`.
It changes only the image lines in `compose.yaml`: consul, traefik and
dashboard, and the one-shot `consul-data-init` too.
It also rewrites `consul.d/00-lvh-yard.hcl`.
Then run `lvh-yard ws compose recreate`,
even if every image is current. This makes Consul load the generated options.

`ws compose upgrade` also runs the migrations that a release adds. A migration
changes a file that LVH Yard owns, or tells you what to change in a file that
you own. It runs once for a folder. The file `.lvh-yard-layout` in the folder
records how many migrations the folder has had. Do not edit it.

A Linux or macOS folder from an older `lvh-yard` mounts `./consul-data` and keeps
doing so, because the upgrade changes only image lines. The first upgrade prints
a note about it. `ws compose down -v` does not remove that folder. To use the
Docker volume, export a Backup, make a new
folder with `lvh-yard ws init --dir <new folder>` and the same Workstation name
and Gossip Key, start it, and run `lvh-yard import <file>`.

LVH Yard owns `consul.d/00-lvh-yard.hcl`, `consul.d/10-tls.hcl`,
`traefik/dynamic/10-tls.yaml` and `.lvh-yard-layout`. Generated files start with
`# Generated, do not edit.` The TLS files exist only when TLS is set up.
The scripts in `scripts/` start Consul, Traefik and, on Windows, the dashboard.
`compose.yaml` mounts each one at `/scripts/` in its container.
`ws init` writes them without a notice, so you can change them.
`ws compose upgrade` writes them again and replaces your changes. It adds a
notice to each script, so you can see that a command wrote it. To keep a change,
copy the script and point `compose.yaml` at the copy. If a script stops working
after you change it, fix it by hand.
You own `compose.yaml`, `.env`, `lvh-yard.yaml` and every other file you add
to `consul.d/` or `traefik/dynamic/`. Put custom options in your own files.
`ws compose upgrade` keeps their contents unchanged.
Consul reads `consul.d/` read-only. On Linux and macOS, Traefik watches the
read-only `traefik/dynamic/` mount. Windows stages the same source files locally.

### Addresses and ports

Traefik and the dashboard listen on `127.0.0.1`, so only this computer reaches
them. To change that, set `YARD_TRAEFIK_BIND_ADDRESS` or
`YARD_DASHBOARD_BIND_ADDRESS`. On Linux and macOS,
`YARD_TRAEFIK_BIND_ADDRESS` accepts an IPv4 address.
Use only `127.0.0.1` or `0.0.0.0` for `YARD_DASHBOARD_BIND_ADDRESS`, because
its health check calls `127.0.0.1`. With another address, Compose reports the
dashboard as unhealthy.
On Windows, use only `127.0.0.1` or `0.0.0.0` for both.
`0.0.0.0` listens on every address, loopback too.

If Traefik does not listen on loopback, the `lvh.me` hostnames do not work on
this computer, because they resolve to `127.0.0.1`.

The dashboard has no login. Anyone who reaches it can change the Services of
this Workstation, and its containers through the Docker socket.

The admin page of Traefik is at `127.0.0.1:8080/dashboard/`, and its ping is at
`127.0.0.1:8080/ping`. They are always on loopback, whatever
`YARD_TRAEFIK_BIND_ADDRESS` says. `YARD_TRAEFIK_ADMIN_PORT` changes the port.

### The dashboard image

The folder that `lvh-yard ws init` writes runs the dashboard from its image,
`ghcr.io/ppalazon/lvh-yard-dashboard`. The image tag is the version of
`lvh-yard`. `ws init` sets these values in `.env`:

- `COMPOSE_PROFILES=dashboard,traefik`, which turns on both services in Compose
- `YARD_DOCKER_GID`, the group that owns the Docker socket
  (`stat -c %g /var/run/docker.sock`)
- `YARD_UID` and `YARD_GID`, your user and group

Consul and the dashboard run as your user, not as root. The dashboard joins the socket group so that
it can read the Docker socket. It uses host networking. On Docker Desktop, turn
on host networking in the settings.

## Installing the CLI

Each release has `lvh-yard` as one executable for Linux (amd64, arm64), macOS
(arm64) and Windows 11 (x64). It needs no Java. To move to a newer release
later, run `lvh-yard update` (see [Upgrading](#upgrading)).

### Linux and macOS

The install script downloads the archive of your platform from the
[releases](https://github.com/ppalazon/lvh-yard/releases), checks it against
`SHA256SUMS`, and copies `lvh-yard` to `~/.local/bin`. As root, it uses
`/usr/local/bin`.

```sh
curl -fsSL https://lvhy.cc/install.sh | sh
```

If `~/.local/bin` is not on your `PATH`, the script tells you the line to add.
Two environment variables change what it does:

- `LVH_YARD_VERSION`, a release such as `0.1.0`. The default is the latest
  release.
- `LVH_YARD_INSTALL_DIR`, the folder for `lvh-yard`.

```sh
curl -fsSL https://lvhy.cc/install.sh \
  | LVH_YARD_VERSION=0.1.0 LVH_YARD_INSTALL_DIR=/usr/local/bin sudo -E sh
```

To install by hand, download the archive from the releases page. Make sure that
it matches `SHA256SUMS`, and put `lvh-yard` on your `PATH`:

```sh
sha256sum -c SHA256SUMS --ignore-missing
tar -xzf lvh-yard-0.1.0-linux-amd64.tar.gz lvh-yard
sudo mv lvh-yard /usr/local/bin/
```

On macOS, the system blocks a downloaded executable. To unblock it, run
`xattr -d com.apple.quarantine lvh-yard`.

### Windows 11 x64

The installer installs the Windows ZIP of a release,
`lvh-yard-<version>-windows-amd64.zip`. It installs the CLI only. To set up a
Workstation, see [Windows Workstation setup](#windows-workstation-setup).

In Windows PowerShell 5.1 or PowerShell 7, read the installer before running it:

```powershell
$installer = Invoke-RestMethod https://lvhy.cc/install.ps1
$installer
Invoke-Expression $installer
```

The installer needs no administrator rights. It checks the Windows ZIP
against the release's `SHA256SUMS`, then installs `lvh-yard.exe` in
`$env:LOCALAPPDATA\lvh-yard\bin`. This bin folder is separate from the
Workstation Folder. It adds the bin folder to your user `PATH` without
duplicates and leaves machine `PATH` unchanged. Open a new terminal, then
run `lvh-yard --version`.

To choose a version or destination, set these variables before running the
installer:

```powershell
$env:LVH_YARD_VERSION = '0.1.0' # v0.1.0 also works
$env:LVH_YARD_INSTALL_DIR = "$env:LOCALAPPDATA\My tools\lvh-yard\bin"
Invoke-Expression $installer
```

Without these variables, the installer uses the latest release and its
default bin folder. To upgrade or reinstall, run it again. A download,
checksum or replacement failure preserves the installed executable. If
Windows locks that file, close the process using it, then rerun the installer.
To return to an earlier version, set `LVH_YARD_VERSION` to that release and
rerun it.

For a manual installation, download the Windows x64 ZIP and `SHA256SUMS`
from the same [release](https://github.com/ppalazon/lvh-yard/releases).
In their download folder, substitute the downloaded ZIP's name below:

```powershell
$archive = 'lvh-yard-0.1.0-windows-amd64.zip'
$line = Get-Content -LiteralPath .\SHA256SUMS |
    Where-Object { $_.EndsWith("  $archive") }
if (@($line).Count -ne 1) { throw 'Expected one checksum for this ZIP.' }
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $archive).Hash -ine
    ($line -split '  ', 2)[0]) { throw 'ZIP checksum mismatch.' }
$bin = "$env:LOCALAPPDATA\lvh-yard\bin"
Expand-Archive -LiteralPath $archive -DestinationPath $bin
& "$bin\lvh-yard.exe" --version
```

The ZIP contains `lvh-yard.exe` and `LICENSE`. Choose an empty destination
for manual extraction. To run `lvh-yard` without its full path, add that folder
to your **user** Path in Windows Environment Variables, then open a new terminal.
Keep the old executable until the new one reports the expected version.

The Windows executable is unsigned. Windows may warn about an unknown
publisher or block a downloaded file. A matching SHA256 checksum checks
download integrity against the release manifest; it does not authenticate an
Authenticode publisher. Follow your organization's security policy before
running it. The installer does not relax execution policy, install Docker,
or change Docker settings or firewall rules.

### Shell completion

`lvh-yard completions <shell>` prints a completion script for Bash, Zsh or PowerShell.
It completes commands, subcommands, flags and known values, such as Service
states. It does not fetch Service names from the dashboard.
The `completions` command stays out of the main help menu.

For Bash, load the script in your current shell:

```bash
source <(lvh-yard completions bash)
```

To load it in each new Bash session, add that line to `~/.bashrc`.

For Zsh, load the script in your current shell:

```zsh
source <(lvh-yard completions zsh)
```

To load it in each new Zsh session, add that line to `~/.zshrc`.
The Zsh script is a native Zsh script. It calls `compdef`, so run `compinit`
before you load it. Most Zsh setups do this already.

For PowerShell, load the script in your current session:

```powershell
lvh-yard completions powershell | Out-String | Invoke-Expression
```

To load it in each new session, add that line to your PowerShell profile.
The completion script is tested in Windows PowerShell 5.1 and PowerShell 7.

## Using the CLI

`lvh-yard` talks to the dashboard on this Workstation. It uses the first
address of this list:

1. `--dashboard`
2. `YARD_DASHBOARD_URL`
3. the address in the `.env` that `init` wrote, in `YARD_CONFIG_DIR` or the
   default folder
4. `http://127.0.0.1:7780`

It shows the whole Yard, but it changes only the Services of this Workstation:

```sh
lvh-yard catalog                            # one line per Service, by Workstation
lvh-yard catalog --workstation ws-b          # one Workstation
lvh-yard catalog --state degraded            # one Service state
lvh-yard catalog --scope every              # also the Remote Services with no Route here
lvh-yard catalog --scope yard               # only Services with Yard Scope
lvh-yard catalog --details                  # Instance addresses, Health, IDs and Service Warnings
lvh-yard catalog --details --workstation ws-b --state degraded
lvh-yard service create web                 # an empty, Unpublished Service, unless it exists
lvh-yard service delete web                 # with its Publication, Static Instances and Container Bindings
lvh-yard register static web --port 8080    # at this Workstation's LAN address
lvh-yard register static web --host 127.0.0.1 --port 8080
lvh-yard register static web --port 8080 --path /health --interval 5s --timeout 1s   # an HTTP check (default: TCP, 10s, 2s)
lvh-yard register static web --port 8080 --tag backend --meta owner=pablo           # repeat --tag and --meta for more
lvh-yard containers                         # running containers that no Service follows
lvh-yard containers --state all --followed all   # every container, as the Docker list
lvh-yard containers --details               # each container and its Host Port mappings
lvh-yard register container shop-web-1     # bind only this named container
lvh-yard register container shop-web-1 --port 9000 --service shop
lvh-yard register compose shop/web --port 80 # bind the Compose service web of project shop
lvh-yard remove web --port 8080             # add --host if several Instances share the port
lvh-yard remove web --id <id>               # the ID that lvh-yard catalog --details shows
lvh-yard remove web --container web-1       # add --port if web-1 is bound on several ports
lvh-yard remove web --compose-project shop --compose-service web
lvh-yard publish web                        # http://web.<base-domain> (Local Scope)
lvh-yard publish web --scope yard           # also on every other Workstation (Yard Scope)
lvh-yard publish web --profile basic-auth     # apply a Route Profile; repeat for more
lvh-yard publish web --no-profiles            # remove all Route Profiles
lvh-yard publish web --access-log on          # Traefik logs the requests to web
lvh-yard publish web --mutual-tls on          # Traefik calls the Instances of web over mutual TLS
lvh-yard unpublish web
lvh-yard priority web                       # the Instance Sources in order, with their IDs
lvh-yard priority web <id> <id>             # put these sources first (Instance Priority)
lvh-yard priority web --off                 # every passing Instance shares the Route
lvh-yard alias add web --workstation ws-b --service web      # web also uses the Instances of web on ws-b
lvh-yard alias list web                                       # the Aliases of web, with their state
lvh-yard alias remove web --workstation ws-b --service web
lvh-yard profiles                           # the Route Profiles, with their types and the Services that use them
lvh-yard profiles show basic-auth           # one Route Profile, in Traefik's file format
lvh-yard profiles set profiles.yaml        # add or replace each Route Profile of the file
lvh-yard profiles show basic-auth | lvh-yard profiles set -   # - reads standard input
lvh-yard profiles edit basic-auth           # change one Route Profile in $EDITOR
lvh-yard profiles remove basic-auth
lvh-yard export                             # a Backup of this Workstation's setup, as YAML
lvh-yard export -o ws-a.yaml                # write it to a file that only you can read
lvh-yard import ws-a.yaml                   # add what is new in a Backup, and skip what exists
lvh-yard import --update ws-a.yaml          # also overwrite what exists with the Backup
lvh-yard import --dry-run ws-a.yaml         # show the plan, and change nothing
lvh-yard test web                           # tests whether this Workstation reaches web
lvh-yard test grafana --workstation ws-b    # a Remote Service
lvh-yard status                             # whether LVH Yard works
lvh-yard status --strict                    # also exits with 1 on a warning
```

`lvh-yard catalog` shows aligned columns for the Service name, state, Scope,
hostname and Instance count. The Scope is `local` or `yard`, and empty for a
Service that is not published. The count says "N/M": the passing Instances
over all Instances. With the Instance Priority
on, it counts only the Instances of the Instance Source that the Route uses,
because the other sources wait. The Service page and the Catalog page of the
dashboard count the same way. It says "Empty" for a
Service with no Instances and no Container Bindings, and "No Instances" for a
Service whose Container Bindings follow no running container.
A last column names the kind of Instance Source that the Route uses now, in
brackets: `[static]`, `[docker]`, `[compose]`, `[alias]` or `[foreign]`. While
the Instance Priority is off, or when no source has a passing Instance, it
names every kind that gives the Route an Instance. A Service with no Route has
no kind. The Catalog page of the dashboard shows the same kinds as badges in its
Instances column, next to the count.
Unpublished Services show the count too, but have no hostname. By default,
the command prints no Instance IDs or Service Warnings. Add `--details` to
show each Instance's address, Health and ID, followed by the Service Warnings.
Both views support `--workstation`, `--state` and `--scope`. You can combine the filters.
The states are `unpublished`,
`unavailable`, `unhealthy`, `degraded` and `healthy`. The Scope filter works as
on the Catalog page. By default (`in-range`), it hides the Remote Services that
this Workstation has no Route to. `every` shows all Services. `local` and `yard`
show only the Services with that Scope, and hide unpublished ones. An
unreachable Workstation
shows `Workstation unreachable` instead of its Services, and the output ends with
the command that shows this Workstation's Consul log: `lvh-yard ws compose logs consul`.

A command exits with 0 when it succeeds. If it fails, it prints the reason on
stderr and exits with 1. A usage error, such as an unknown option, does the same.

`lvh-yard register` attaches an Instance to a Service that exists. If the
Service does not exist, the command fails and names the command that creates
it, such as `lvh-yard service create web`.

`lvh-yard profiles show` and `lvh-yard profiles set` use Traefik's file format:
a YAML document with `http.middlewares.<name>`. So the output of `show` works as
input to `set`:

```yaml
http:
  middlewares:
    office-only:
      ipAllowList:
        sourceRange:
          - 192.168.1.0/24
    protected:
      chain:
        middlewares:
          - office-only
```

`lvh-yard profiles set` saves all profiles of the file, or none. A chain can
name a profile of the same file. The profiles that the file does not name stay.
If the dashboard refuses the file, the command prints each reason. If a
Publication or a chain names a profile, `lvh-yard profiles remove` refuses and
names each Service and chain.

`lvh-yard profiles edit <name>` opens the profile in the editor named by `EDITOR`,
in the format of `show`. When `EDITOR` is empty, it opens `vi` on Unix or
`notepad` on Windows. After you close the editor, the command saves the profile.
If you did not change the text, it saves nothing.
The file must hold only that one profile, with the same name. If LVH Yard
refuses your change, the command prints the reason and asks
`Edit again? [Y/n]`. Press Enter to open your text again. Answer `n` to stop. The
command also stops if the input ends or the editor fails. Then it saves nothing,
exits with 1 and prints the path of the file that holds your text. After a
save, or when nothing changed, it deletes the file.

In PowerShell, set the editor with `$env:EDITOR`. A whole executable path can
contain spaces without extra quotes inside the value. If you add arguments,
put double quotes around the executable path inside the value:

```powershell
$env:EDITOR = '"C:\Program Files\My Editor\editor.exe" --wait'
```

Choose an editor mode that waits until you finish the file. Windows invokes
the executable directly, without a Unix shell. Printed Windows commands use
PowerShell quoting: a single quote inside a single-quoted value appears twice.
Keep that quoting when you paste a printed command.

The `/status` page of the dashboard and `GET /api/status` show the same status
as `lvh-yard status`.

`lvh-yard status` ends with the address of the dashboard. Open it in a browser.

## Backups

A Backup is one YAML file that holds what you set up on one Workstation:

- each Service that you created on this Workstation, empty ones too. A Service
  holds its Publication, its Instance Priority and its Instance Sources in
  their order: Static Instances, Container Bindings and Aliases. LVH Yard's own
  Services, such as `yard`, are in it too.
- the Route Profiles of this Workstation

A Backup never holds Bound Instances, Foreign Instances, Health or Remote
Services. A Container Binding gives its Bound Instances again when its
containers run. Another tool registered a Foreign Instance, so that tool must
register it again.

`lvh-yard export` prints the Backup. `lvh-yard export -o <file>` writes it to a
file. The API gives the same Backup at `GET /api/workstation/backup`, as
`application/yaml`:

```sh
curl -H 'Accept: application/yaml' http://127.0.0.1:7780/api/workstation/backup
```

A Backup looks like this:

```yaml
version: 3
exported-from:
  workstation: ws-a
  at: "2026-11-05T10:00:00+01:00"
route-profiles:
  team-login:
    basicAuth:
      users:
        - ana:$2y$05$abc
  certificate:
    clientCertificate: {}
services:
  grafana:
    publication:
      scope: yard
      route-profiles:
        - team-login
      access-log: true
      mutual-tls: true
    instance-priority: true
    sources:
      - static-instance:
          port: 3000
          check:
            type: http
            path: /health
            interval: 10s
            timeout: 2s
          tags:
            - dashboards
  shop:
    publication: none
    instance-priority: false
    sources:
      - container-binding:
          container: shop-web-1
          port: 80
      - alias:
          workstation: ws-b
          service: shop
      - container-binding:
          compose:
            project: shop
            service: worker
          port: 9000
  notes:
    publication: none
    instance-priority: true
```

- `publication: none` means that the Service is Unpublished.
- `instance-priority: false` means that the Instance Priority is off. Then
  every passing Instance of every source shares the Route.
- `sources` lists the Instance Sources in the order of the Instance Priority.
  Each entry has one key: `static-instance`, `container-binding` or `alias`.
- A Service with no `sources`, such as `notes`, is an empty Service.
- `access-log: true` means that Traefik logs the requests to the Service. If
  the Backup leaves it out, Traefik does not log them.
- `mutual-tls: true` means that Traefik calls the Instances over HTTPS with its
  certificate. If the Backup leaves it out, it uses plain HTTP.
- If the host of a Static Instance is the LAN address of this Workstation, the
  Backup leaves the host out. So the Static Instance fits another computer.
- The Backup leaves out empty tags and meta.

Exports use version 3. Imports accept versions 2 and 3. Version 3 holds
Client Certificate Route Profiles, such as `certificate` above.

A Backup holds the basic-auth hashes of your Route Profiles. Anyone who can
read the file can read them. Keep a Backup as private as a password file.
`lvh-yard export -o` writes the file so that only you can read it.

To move between operating systems, export a Backup, create a fresh Workstation
on the destination, then import the Backup there.
On Windows, follow [Move a Workstation to another platform](#move-a-workstation-to-another-platform).
Recreate its environment settings and install its own certificates separately.
A Backup contains no private certificates, raw Consul data or Compose files.
Do not copy a Consul data folder or Docker volume across operating systems.
Keep the destination's generated Compose setup.
The Backup holds the own Services of the source, such as `yard`, with their
Static Instances. If the two Workstations use different ports, the import adds
the source's Instances to the own Services of the destination. They stay
critical, because nothing listens there. Remove them with `lvh-yard remove`.

### Importing a Backup

An import has two modes:

- `lvh-yard import <file>` adds what is new in a Backup to this Workstation. It
  skips what exists, so it never overwrites your changes.
- `lvh-yard import --update <file>` also overwrites what exists with the values
  of the Backup. Use it to bring a Workstation back to a Backup.

`lvh-yard import -` reads the Backup from standard input. So you can copy the setup of another
Workstation:

```sh
ssh ws-b lvh-yard export | lvh-yard import -
```

The import prints one line for each entry of the file. Each line says `add`,
`update` or `skip`. A skip gives its reason:

```text
Backup of ws-b at 2026-11-05T10:00:00+01:00.
add Route Profile team-login
skip Service grafana: It exists already.
add Static Instance 192.168.1.20:3000 of grafana
skip Publication of grafana: It exists already.
Added 2, skipped 2.
```

With `--dry-run`, the import prints the same plan and changes nothing.

The import finds what exists in this way:

- a Service and a Route Profile by their names
- the Instance Priority of a Service by its order and toggle
- an Alias by its Remote Service, inside its Service only
- a Static Instance by its host and port, inside its Service only
- a Container Binding by its ID, such as `shop-web-1:80` or
  `compose:shop:worker:9000`

With `--update`, the import overwrites what it finds:

- the Publication of a Service: its Scope and Route Profiles
- the Instance Priority of a Service: the order of the sources of the file and
  the toggle. Sources that the file does not name, such as Foreign Instances,
  come after them.
- the health check, tags and meta of a Static Instance. The Instance keeps its
  ID.
- the definition of a Route Profile

A Container Binding and an Alias have nothing more to change, so the import
always skips one that exists.
An entry with the same values is a skip in both modes.

A Static Instance without a host is at the LAN address of this Workstation. A
new Service gets the order of the file and its toggle. A new Service without a
`publication` key stays Unpublished. An existing Service
without a `publication` key keeps its Publication, in both modes. With
`--update`, `publication: none` unpublishes an existing Service. A Publication
can name a Route Profile of the same file.

Before the import changes anything, the dashboard checks the whole file. If one
entry is wrong, the import changes nothing, prints each problem with its place
in the file, and exits with 1:

```text
services.grafana.sources[0].static-instance.port: The port 70000 is not a TCP port: use 1 to 65535.
services.grafana.publication.route-profiles[0]: The Route Profile login is not in the file, and this Workstation does not have it.
```

The import refuses a key that a Backup does not have, a version other than 2 or 3,
and an unknown Route Profile. It also refuses an Alias to this Workstation.

The import writes Route Profiles first, then Services, then Static Instances
and Container Bindings, then Publications, then Aliases, then the Instance Priority. It adds an Alias even
when its Remote Service has no Yard Scope now. Consul cannot write them all at
once. So if a write fails halfway, the import stops. It lists each entry that
it wrote before, and it gives the name of the safety Backup:

```text
The import stopped at Publication of grafana: Consul does not answer.
It applied:
add Route Profile office
add Static Instance 192.168.1.20:3000 of grafana
Safety Backup: ws-a-20261105-101500-before-import.yaml, in the Backups folder. To go back, import it.
```

The API imports a Backup at `POST /api/workstation/backup?mode=add`, with the
Backup as `application/yaml`. Use `mode=update` to overwrite. Add `dry-run=true` for the plan only. The answer
is the plan as JSON. A refused file answers 400, with a `problems` list of
`place` and `reason`. A write that fails halfway answers 500, with an `applied`
list of the entries that the import wrote and the `safetyBackup` name:

```sh
curl -X POST -H 'Content-Type: application/yaml' --data-binary @ws-a.yaml \
  'http://127.0.0.1:7780/api/workstation/backup?mode=add&dry-run=true'
```

### The Backups folder and the safety Backup

The Backups folder is `backups/` in the folder of this Workstation, such as
`~/.config/lvh-yard/backups`. `lvh-yard ws init` creates it. `compose.yaml` lets
the dashboard write to this folder, and to no other folder of this
Workstation. Each Backup in it is a plain YAML file. You can copy, move or
delete the files with normal tools. The dashboard never deletes them by itself.

Before an import changes anything, the dashboard writes a safety Backup: a
Backup of the setup before the import. Its name is
`<workstation>-<yyyyMMdd-HHmmss>-before-import.yaml`. If two imports start in
the same second, the second name ends in `-before-import-2.yaml`. Only you can
read the file, because it can hold basic-auth hashes. The import prints its
name after the plan:

```text
Added 2, skipped 2.
Safety Backup: ws-a-20261105-101500-before-import.yaml, in the Backups folder.
```

An import that changes nothing, and a dry run, write no safety Backup. The API
gives the name as `safetyBackup` in its answer.

To get back the values that an import overwrote, import its safety Backup with
`lvh-yard import --update`. An import never removes what it added. So to undo
an add, remove those entries by hand.

### The Backups page

The Backups page of the dashboard, at `/backups`, lists the `.yaml` and `.yml`
files of the Backups folder. Each row shows the name of the file, the date from
its `exported-from`, and how many Services, Aliases and Route Profiles it
holds. An invalid file shows **Cannot import**. Open **Problems** to see each
error. On a phone, the rows stack their details and actions below the filename.

- To make a Backup of the current setup, click **New Backup**. Its name is
  `<workstation>-<yyyyMMdd-HHmmss>.yaml`, so the names sort by date.
- To keep a Backup in another place, click **Download** in its row.
- To delete a Backup, click **Delete** in its row. The page asks you to
  confirm. Then click **Delete** again.
- To add a Backup from your computer, such as a file from a teammate, click
  **Upload a Backup**. The form opens on its own page, `/backups/upload`.
  Choose the file and click **Upload**. To go back to the list, click
  **Cancel**. The file keeps its name. The page refuses a name with a
  folder in it, a name that starts with a dot, a name that does not end in
  `.yaml` or `.yml`, a name that is already in the Backups folder, and a file
  that is not a Backup. If the page refuses the file, the form shows why under
  the file field. The browser does not keep the file, so
  choose it again.

To import a Backup from the page, do these steps:

1. Click **Import** in its row to open **Plan a Backup importation**.
   A file that is not a Backup has no **Import**.
2. Choose the mode. **Add** adds what is new and skips what exists. **Update**
   also overwrites what exists with the values of the file.
3. Click **Show the plan**. **What's going to change** lists additions and
   updates. The gray **Skipped** panel lists skipped entries and their reasons.
   Both headings show entry counts. Nothing changes yet. If the file has
   problems, the page shows each one, and you cannot import it.
4. To import, click **Import** in the plan header. **What the import did** shows
   **Changes** and **Skipped** panels for the result. If the import changed the
   setup, the page also names its safety Backup. To go back, import that Backup
   in update mode.

The plan and result list each panel's entries alphabetically.

The page imports only the plan that you saw. If the file or the setup changed
after you clicked **Show the plan**, the page changes nothing. It shows the new
plan, and you click **Import** again. If a write fails halfway, the page lists
what the import applied and gives the name of the safety Backup.

## How it works

### Services and Publications

You create a Service with a name, and it stays until you delete it. A new
Service is empty and Unpublished, so nothing gets a hostname by mistake. To
create one, click "New Service" in the catalog, give the name and click
Create. The page of the new Service opens. You can also run
`lvh-yard service create <name>`. Creating is safe to repeat. If this
Workstation already has a Service of that name, also one that only Foreign
Instances give, nothing changes: the page of the Service opens, and the command
prints `<name> exists already.` and succeeds. The API answers `201` for a new
Service and `200` for one that exists. The catalog marks a Service with no
Instances and no Container Bindings as Empty.

Static Instances and Container Bindings attach to a Service that exists. A
Static Instance or a Container Binding for a missing Service is refused, and
so is a Publication. The Services are in the Consul KV of this Workstation,
under `lvh-yard/services/`.

To delete a Service, click "Delete Service" at the bottom of its page. A
confirmation page lists what goes: the Publication, the Static Instances, and
the Container Bindings with their Bound Instances. It leaves out what the
Service does not have. Click Delete. The containers keep running. You can also
run `lvh-yard service delete <name>`. If the Service does not exist, the command
says so and succeeds, so you can repeat it.

A Service that only Foreign Instances give shows without being created. It
works like any other Service: you publish it, attach Instance Sources, add
Aliases and set its Instance Priority with no create step. The first of these
changes stores it under `lvh-yard/services/`, so it then stays until you
delete it. A Service with Foreign Instances left stays after a delete while
they are registered, and its page says why.

A Service is `unpublished`, `unavailable` (published, no Instances), `unhealthy`
(published, no passing Instance), `degraded` (published, some Instances pass and
some do not) or `healthy` (published, every Instance passes). Warning and critical
Health both count as not passing. The state counts every Instance, whatever the
Instance Priority or its Out-of-range mark. On its Home Workstation, it also counts the
Instances of the Service's Aliases. Degraded and warning Health have orange
badges. A Degraded Service still routes to its passing Instances.

A Publication covers every Instance of the Service, also the ones that you
register later. The Publications are in the Consul KV of this Workstation,
under `lvh-yard/publications/`.

A Service name is the first part of its hostname, so it is a single DNS label:
1 to 63 lowercase letters, digits and `-`. The dashboard refuses the name
`consul` with this reason: "consul is Consul's own entry in its catalog".
Consul keeps its own server entry in its catalog under that name.

A new Publication has Local Scope: only this Workstation routes it, at
`<service>.<base-domain>`. With Yard Scope, the other Workstations also route
it, at `<service>.<workstation>.<base-domain>`. If you publish again without
`--scope`, the Scope does not change.

A Service with Yard Scope shows a risk for each Instance on loopback, because
other Workstations cannot reach it. The page, the API and `lvh-yard publish`
show this risk.

Traefik does not log the requests to a new Publication. To see them in the
Traefik access log, turn the access log on. Use the Service page or
`lvh-yard publish <service> --access-log on`. Then Traefik writes one line for
each request to the Route. To read the log, run `lvh-yard ws compose logs traefik`;
the Service page shows this command while the access log is on. If you publish
again without `--access-log`, the setting does not change.

You can see a Remote Service, but only its Home Workstation can change it. Its
page has no forms, and the API answers 405 to a change under `/api/services`.

### Service Warnings

A Service Warning is a problem that the setup of a Service causes. Each one has
one of two levels:

- **error**: the hostname does not work. An unknown Route Profile is an error.
- **risk**: the hostname works, but other Workstations or users can meet a
  problem. An Instance on loopback, a container port that is not published, a
  Route Profile that asks who the user is, a Foreign Instance, an Alias
  above another Instance Source of a Service with Yard Scope, and an Instance
  that Traefik calls over plain HTTP while HTTPS is on are risks.

A Foreign Instance is an Instance that another tool registered straight in
Consul, for example with `curl` or the `consul` command. LVH Yard shows it and
routes it as a Static Instance. LVH Yard cannot edit or remove it, so its source
has no Edit or Remove button. A Backup does not hold it. To remove it,
deregister it from the Consul agent of its Home Workstation. For example, on
that Workstation, send this request to the Consul HTTP API. The port is the
value of `YARD_CONSUL_HTTP_PORT`, 8500 by default:

```text
curl -X PUT http://127.0.0.1:8500/v1/agent/service/deregister/<instance-id>
```

An unpublished Service also shows this risk.

The API sends each Service Warning in `warnings`, with its `kind`, its
`severity` (`error` or `risk`), its `topic` and its `message`. The Service state is not a
Service Warning, so an Unavailable or Unhealthy Service has no Service Warning
for its state.

`lvh-yard catalog --details`, `lvh-yard publish` and
`lvh-yard register container` print the level and the topic before each message:

```text
    error: [Unknown profile] The Route Profile team-login does not exist on the Home Workstation. ...
    risk: [Loopback Instances] 127.0.0.1:8080 is on loopback. Other Workstations cannot reach it. ...
```

The Service page shows one alert for each problem, to the right of the hostname.
Each alert has an icon, the topic as its title, and a text that ends with what
to do. Click the title to close or open the text.

- A red alert shows what stops the hostname from working. It starts open. An
  Unavailable Service shows the alert "Unavailable", and an Unhealthy Service
  shows the alert "Unhealthy".
- A risk alert starts closed, so only its title shows.

When the page refreshes, each alert stays open or closed as you left it. If you
leave the page, each alert starts as described above on your next visit.

The Service page starts with its name, state, and the Route Diagram and Test
Reachability buttons.
Under them, the hostname, the passing Instance count, where the Route goes (a
published Service only) and the Home Workstation are on the left. A Service
without a Route on this Workstation has no hostname link. The alerts are on the
right on a wide screen, and
below the facts on a phone.

The Publication card has a switch for each setting: Published, Share with the
Yard, Show in the access log and Mutual TLS to the Instances. A switch changes its
setting as soon as you flip it. A line under each switch says what the setting does
now. The last three switches show while the Service is published. Route Profiles
sits beside the card on a wide screen and below it on a phone. The Instance
Sources card follows them at full width. Each card holds its own controls.
Delete Service is at the bottom of the page. A Remote Service shows the same
facts and alerts, without controls that change it.

### Instance Priority

An Instance Source is one thing that gives a Service Instances: a Static
Instance, a Container Binding, an Alias or a Foreign Instance. A Service keeps
its Instance Sources in one order. The Route uses only the first source in the
order that has an Instance with a passing check. All passing Instances of that
source share the requests, for example the containers of a scaled Compose
service.

For example, `web` has two Static Instances: one in a terminal on port 3000 and
one on port 3001. While the first one passes its check, `web.<base-domain>`
reaches only that one. When you stop it, its check turns critical, and the
Route moves to the second one. When you start it again, the Route moves back.
Each move takes up to about 15 seconds.

A new Instance Source goes to the end of the order, so it never takes over the
Route. A new Foreign Instance goes to the end too. An edited Static Instance or
Container Binding keeps its place. A removed source leaves the order. If you add
it again, it goes to the end.

If no Instance passes its check, the Route uses every Instance in range of
every source, Aliases included. Out-of-range Instances never count.

An Alias source works only on your own Workstation. Other Workstations skip
it, and use the next source in the order. Apart from that, every Workstation
that routes a Service with Yard Scope uses the order of its Home Workstation.
So if an Alias is above another source of a Service with Yard Scope, the
Service has a risk: your Route and your teammates' Routes can reach different
copies.

To see the order with the ID of each source:

```sh
lvh-yard priority web
```

To change the order, give the IDs. They go first, in this order, and the other
sources follow in their order:

```sh
lvh-yard priority web <id> <id>
```

You can also turn the Instance Priority off. Then every passing Instance of
every source shares the Route, Aliases included. If no Instance passes, the
Route uses every Instance in range, Aliases included. The order stays. When you turn the
Instance Priority on again, the order is back:

```sh
lvh-yard priority web --off
lvh-yard priority web --on
```

The order and the toggle are in the record of the Service, not in its
Publication. When you unpublish and publish again, they stay.

On the page of the Service, the Instance Sources card lists the sources in
order, each with its Instances. The Instance Priority switch turns the order on
and off, and the buttons that add a source sit beside it. Each source has an up
and a down icon button at the right of its title. They keep the same place in
every source. The first source has no up button and the last has no down
button, and their places stay empty. The icons change the order. They hide while
the Instance Priority is off. The
source that the Route uses now has a green "In use" badge. A source that gives
no Instance, or none that passes, has a red "Unavailable" badge instead. The
source under the
pointer gets a highlighted background, so you see which source you change. The
page of a Remote
Service shows its sources in order, without the switch, the buttons and the
highlight.

Each source has a short title: a badge for its type (Static, Container, Compose,
Alias or Foreign), then what tells it apart, then a Port badge for a Container
Binding. For example, a Compose Binding reads "Compose", "shop / web" and "Port
80". The header of the page names the source in use in the same way after
"Routes to:", and says how many sources wait. While Instance Priority is off, it
shows "Every passing Instance".
`lvh-yard catalog --details` marks each Instance that the Route uses with
`routed`.

In the API, `sources` lists the sources of a Service in order. Each source has
an `id`, a `kind` and its `instances`. The kinds are `static`, `bound`, `alias`
and `foreign`.
`instancePriority` says whether the order is on. `routing.source` is the ID of
the source in use, and `routing.waiting` lists the IDs of the sources that
wait. While the Instance Priority is off, `routing` has `"off": true`.
`GET /api/workstation/services/{service}/instance-priority` reads the order and
the toggle, and `PUT` on the same path sets them with `order` and `on`.

### Out-of-range Instances

An address on loopback or on a Docker bridge network (172.17.0.0/16 to
172.31.0.0/16) is out of range. If an Instance of a Remote Service has such an
address, it is an Out-of-range Instance here. A Route to it reaches this
computer instead. So no Route points to it, and the API (`outOfRange`), the
page and `lvh-yard catalog --details` mark it.

If your LAN uses these addresses, set `YARD_OUT_OF_RANGE` to your own list. The
test Yard leaves loopback out, because its two Workstations share it.

If a Workstation stops answering, or its Services cannot be read, only its own
Routes are dropped. When it answers again, they come back.

### Reachability tests

A Reachability test tells whether this Workstation reaches a Service. Click
"Test Reachability" on the Service page to open the results on a separate page.
Use "Test again" to run another test, or "Back to Service" to return. The results
do not refresh by themselves.

You can also run `lvh-yard test`, or send
`GET /api/services/<workstation>/<service>/reachability`.

The test connects to each Instance directly, also to the Instances of the Service's
Aliases. The page names the Alias that gives an Instance. It also sends a request for the
hostname of the Service through the local Traefik, over HTTP.
If the Service has no Route here, only the Instances are tested, and the result
says why. An Out-of-range Instance is skipped, and that is not a failure.

The result also says which Instances the Route uses now, under the Instance
Priority. So an Instance that is reached but not in the Route is left out by
the order, not broken. When the Route uses an Alias, the result names the
Remote Service that gets the requests. While the Instance Priority is off, the result
says so.

If Traefik has no Route for the hostname, or answers 502, 503 or 504, the
request through Traefik fails. It also fails when Traefik answers 500 with its
own text, `Internal Server Error`. That happens when Traefik cannot set up TLS
with an Instance, for example when a Service with mutual TLS has an Instance whose
certificate does not name the Service. `lvh-yard ws compose logs
traefik` shows the reason. Any other answer means that the request reached the
Service. The results are not stored. If a test fails, `lvh-yard test` exits
with 1.

When [HTTPS](#https-routes) is on, the test also sends a request over HTTPS, to
the HTTPS port of Traefik. The result shows one line for each scheme, such as
`Route http://web.lvh.me` and `Route https://web.lvh.me`. The HTTPS request
does not check the certificate of Traefik, because the dashboard does not trust
the CA of this Workstation. It tests that the Route answers, not who signed the
certificate.

A Route Profile with a Client Certificate has no HTTP Route, so it gets only the
HTTPS request. The test has no Client Certificate, so Traefik refuses it. That
refusal shows that the Route exists, and the result says so instead of giving a
status.

### Route Diagram

The Route Diagram shows how a request reaches a Service. Click "Route Diagram"
on the Service page. It draws the setup as this Workstation sees it, from the
browser down to the Instances, and sends no request. For a Remote Service, the
Instances are in a box for the Home Workstation. The page refreshes by
itself when the Yard changes. A phone scrolls the picture sideways.

The picture shows:

- The browser, then the HTTP and HTTPS listeners of Traefik with their address
  and who can connect. HTTPS is shown only when its TLS Part is on. A Route
  Profile that needs a Client Certificate leaves only HTTPS.
- The Route and its hostname, the Route Profiles in the order the Route applies
  them, and how the Route shares requests. A key marks a middleware that asks
  who the user is.
- The Instances, in two boxes: green for loopback addresses and blue for
  every other address. Each Instance shows its Health, its Instance Source and
  where its address leads: loopback, a Docker network or a network address.
- The Scope. For a Yard Service, the picture names the hostname that other
  Workstations use, and says how many Instances they skip.

A green box is on and in the path of a request. A dashed box is off, or the
Route does not use it: an Instance that waits, or that this Workstation skips
as an Out-of-range Instance.

Each wire shows the protocol of its step. A red wire with an open lock is plain
HTTP. A green wire with a closed lock is TLS. A Service with mutual TLS on
makes the step to the Instances mutual TLS. A grey dashed wire is not used.

Next to the picture, the page lists only what the picture leaves out: how to
turn HTTPS on, the certificate rules for a Client Certificate and for mutual
TLS, the options of each Route Profile, and the effect of Instance Priority.
A Route Profile can hold a secret, so the page never shows the `users` of
`basicAuth` or `digestAuth`, `usersFile`, the options of a plugin, or the user,
password and query of a `forwardAuth` address. Header values are shown.

If the Service has no Route on this Workstation, the page says why and draws
only the Instances. Use Test Reachability to see whether a request really gets
through.

### Aliases

An Alias is an Instance Source of a Service. It gives the Service the
Instances of a Remote Service, for example `web` of your teammate's Workstation
`ws-b`. An Alias has no hostname of its own: the Route of your Service uses its
Instances, under the Instance Priority. Only your Workstation uses your
Aliases. Other Workstations skip them.

A Service can have many Aliases, for example one to `ws-b` and one to `ws-c`.
Put them in order: while the first one has no passing Instance, the Route uses
the next one. A new Alias goes to the end of the order. The Aliases are in the
record of the Service, in the Consul KV of this Workstation.

To add an Alias from the dashboard, open the page of a Remote Service from the
catalog, and click "Alias". Only Remote Services with Yard Scope have this button. The form opens on its
own page. Choose one of your Services, or type a new name. The default is the
name of the Remote Service. Then click "Add":

- If the Service does not exist, the dashboard creates it and publishes it with
  Local Scope. So its hostname works at once.
- If the Service exists, it keeps its Publication. The Alias goes to the end of
  its order, and the Service page opens at its Instance Sources card.

If the dashboard refuses the Alias, the form shows the reason under its field.
Cancel goes back to the catalog.

To add an Alias on the Service page, click "Add Alias" in the Instance Sources
card. The form opens on its own page and lists the Remote Services with Yard
Scope. Cancel goes back to the Service page. If the dashboard refuses the
Alias, the form shows the reason under its field. You can also run the command
below. As the "Alias" button does, it creates a missing Service and publishes
it with Local Scope:

```sh
lvh-yard alias add web --workstation ws-b --service web
lvh-yard alias list web
lvh-yard alias remove web --workstation ws-b --service web
```

`lvh-yard alias add` says which case happened:

```text
Added the Alias to web on ws-b to the existing Service web.
Created Service web with Local Scope and added the Alias to web on ws-b.
Service web has the Alias to web on ws-b already.
```

Adding an Alias that the Service has already is safe to repeat. Nothing
changes, the Alias keeps its place in the order, and the command succeeds. The
form opens the page of the Service.

The dashboard refuses a new Alias in these cases:

- The Service does not exist, on the Service page or in the API without
  `create`.
- The Remote Service is on this Workstation.
- The Remote Service is not there with Yard Scope now, or its Workstation does
  not answer.

An Alias stays when its Remote Service loses Yard Scope or its Workstation
stops answering. It then gives no Instances, and the Service page says why.
When the Remote Service has Yard Scope again, the Alias gives its Instances
again. Each Alias on the Service page links to the page of its Remote Service,
and shows its Instances.

These are the states of an Alias, which `lvh-yard alias list` and
`GET /api/workstation/services/{service}/aliases` show:

- `active`: it gives the Instances of the Remote Service
- `unavailable`: the Remote Service has no Instance in range here
- `not-shared`: the Remote Service is unpublished or has Local Scope
- `unreachable`: its Workstation does not answer
- `missing`: the Remote Service or its Workstation is gone

`POST /api/workstation/services/{service}/aliases` with `workstation` and
`service` adds an Alias. With `"create": true`, a missing Service is created
and published with Local Scope. The answer is the Alias. Its `serviceCreated`
field is `true` when the Service was created, or `false` when the Alias joined
an existing Service. Its `aliasCreated` field is `false`, with status `200`
instead of `201`, when the Service had the Alias already. `DELETE
/api/workstation/services/{service}/aliases/{workstation}/{remoteService}` removes
one. The ID of an Alias source is `<workstation>/<service>` of its Remote
Service.

### Route Profiles

A Route Profile is a named set of Route options, such as basic auth, added
headers or a required Client Certificate.
The Consul KV of this Workstation holds its Route Profiles, under
`lvh-yard/route-profiles/<name>`. Other Workstations read the profiles there.
A profile is one middleware in the format of Traefik, such as
`{"headers": {"customRequestHeaders": {"X-Forwarded-Proto": "https"}}}`,
or the reserved option `clientCertificate: {}`.

The Route Profiles page of the dashboard lists each profile with its name, its
Traefik type and the Services whose Publications name it. In **Used by**, click a
Service to open its page.

- To add a profile, click **New Route Profile**. Type its name and write its
  options as YAML in **Route options**, such as `headers:` with its options
  below it, or `clientCertificate: {}`. Then click **Save Route Profile**.
- To use a reference, open an example in the **Examples** sidebar. Copy its YAML
  into **Route options** and adapt the values before you save. Each example has
  usage notes and a link to the Traefik documentation. Opening an example keeps
  your form unchanged. On a phone, the examples follow the form.
- To see a profile's options without editing, click its row. The page shows its
  YAML. Click **Back to Route Profiles** to return to the list.
- To change a profile, click **Change**. The editor shows its YAML.
  Click **Save changes** to save it. Saving an existing name replaces that
  profile for every Service and chain that uses it.
- To remove a profile, click **Remove** in its row. If a Publication or a chain
  names it, the page refuses and names each Service and chain.

If the dashboard refuses a save, the page shows the reason and keeps your text
in the form. The Service page links each Route Profile of a Publication to its
row on this page. On the Service page, the Route Profiles card has a selector at
the right of its title, with the profiles that the Service does not use yet, and
an **Add** button next to it. Each used profile has three icon buttons at the
right of its row: up, down and a red cross that removes it. They keep the same
place in every row. The first profile has no up button and the last has no down
button, and their places stay empty. The cross takes the profile off the Service
only. It does not delete the profile. The row under the pointer gets a
highlighted background, so you see which profile you change.

You can also add, replace and remove Route Profiles through the API of the
dashboard, under `/api/workstation/route-profiles`:

```sh
dashboard=http://127.0.0.1:7780/api/workstation/route-profiles
curl -X PUT -H 'Content-Type: application/json' "$dashboard/behind-https" \
  -d '{"headers": {"customRequestHeaders": {"X-Forwarded-Proto": "https"}}}'
curl -X PUT -H 'Content-Type: application/json' "$dashboard" \
  -d '{"office-only": {"ipAllowList": {"sourceRange": ["192.168.1.0/24"]}},
       "protected": {"chain": {"middlewares": ["office-only", "behind-https"]}}}'
curl "$dashboard"                 # the list, with types and the Services that use each one
curl "$dashboard/protected"       # one profile, with its definition
curl -X DELETE "$dashboard/protected"
```

- `PUT` on a name adds or replaces that profile.
- `PUT` on the collection adds or replaces each profile of the body. The
  profiles that the body does not name stay. If one profile is refused, the
  dashboard saves none of them.
- A chain names its members, as in Traefik. When you change a profile, each
  chain that names it uses the new version. The chain itself does not change.
- One save writes at most 64 profiles. If a save needs more, the dashboard
  refuses it. Save fewer profiles at a time.
- `DELETE` on a name removes that profile. If a Publication or a chain names
  it, the dashboard refuses with 409 and names each Service and chain.

`lvh-yard profiles` lists the profiles, as the list of the API does.

To require a Client Certificate signed by the CA of the Workstation that serves the Route, save this profile with
`lvh-yard profiles set profiles.yaml`:

```yaml
http:
  middlewares:
    certificate:
      clientCertificate: {}
```

The Route Profiles page takes `clientCertificate: {}` in **Route options**.
The API takes `{"clientCertificate": {}}` as the body of
`PUT /api/workstation/route-profiles/certificate`.
The reserved key has no settings and is not a Traefik middleware.
Publish a Service with `lvh-yard publish admin --profile certificate`.
A chain can name `certificate`, including through another chain.

The Publication answers only on HTTPS. A request without a Client Certificate
from that CA is refused. There is no HTTP Route or redirect.
A Workstation without HTTPS, or with `ws tls client` off, gives it no Route. On
the Home Workstation, this causes a Service Warning of level **error**. See
[Client Certificates](#client-certificates) to make a certificate for a browser.

Dashboard hostname links use HTTPS and the configured HTTPS port for these
Publications, with a lock icon. Other hostname links use HTTP.

Mutual TLS to the Instances is not a Route Profile option. It is a setting of the
Publication: [Mutual TLS to Services](#mutual-tls-to-services).

A profile must work on every Workstation, so the dashboard refuses a profile
that:

- uses a plugin
- reads its users from a file (`usersFile`). Write them under `users`, as
  hashes from `htpasswd -nbB <user> <password>`.
- sends `forwardAuth` to `localhost` or to an out-of-range address
- is a chain that names a middleware that is not a Route Profile of this
  Workstation, or that is refused
- has a type that is neither a Traefik middleware nor `clientCertificate`
- has a name with other characters than letters, digits, `-` and `_`

A refused save answers 400 with the reason, and nothing changes. So a bad save
never opens or breaks a Service.

LVH Yard does not take the middleware types for rate limits (`rateLimit`,
`inFlightReq`) or for path rewrites (`addPrefix`, `stripPrefix`,
`replacePath` and their `Regex` forms). A profile with one of them is refused.

A Publication names zero or more Route Profiles. Choose them on the Service
page, or with `lvh-yard publish`:

```sh
lvh-yard publish admin --profile basic-auth             # admin.<base-domain> asks for a password
lvh-yard publish admin --profile office-only --profile basic-auth   # in this order
lvh-yard publish admin --no-profiles                    # removes them all
```

Without `--profile`, a Publication keeps its profiles. A name that is not a
Route Profile of this Workstation is refused, with the names that exist.

The Route gets each middleware profile as a Traefik middleware named
`<workstation>_<profile>`, such as `ws-a_basic-auth`, in the order of the
Publication. A chain's members get the same name in front. So the profiles of
two Workstations never clash.

A Service with Yard Scope gets its profiles on every Workstation. A teammate's
Route to it applies the same profiles. An Alias gives only Instances, so the
Route of the Service that has the Alias applies its own profiles. Their
dashboard reads the profiles from your Consul KV, so nobody copies a file. When
you change a profile, their Routes change within a few seconds. The Service's
page on their dashboard shows the profile names.

A profile with `basicAuth`, `digestAuth`, `forwardAuth` or `clientCertificate`,
also inside a chain,
protects only the hostname. A connection straight to the Instance's address and
port does not go through Traefik, so it skips the profile. The Service page,
the API and `lvh-yard publish` show a risk about it. The Service must check
its users itself.

If you remove a profile from the file while a Publication still names it, the
Route still lists it. Traefik then refuses the Route, so the hostname does not
answer and a missing auth profile never opens the Service. The Service page,
the API and `lvh-yard catalog --details` show an "unknown Route Profile" error. Add the
profile again, or remove it from the Publication.

### Static Instances

A Static Instance is at a fixed host and port. It stays registered until you
remove it. You add, edit and remove Static Instances on the page of their
Service. A Service can have many Static Instances, for example two copies on
different ports.

To add a Static Instance, open the page of the Service and click "Add Static
Instance" in the Instances part. The form opens on its own page. It asks for the
host, the port, the health check, the tags and the meta. The HTTP path field
shows only while the check is HTTP. If you leave the host empty, the Instance
is at the LAN address of this Workstation. Cancel goes back to the Service
page. If a value is wrong, the form shows your input again with the error under
its field. You can also run `lvh-yard register static`.

The Service must exist first. To create it, click "New Service" in the catalog,
or run `lvh-yard service create`. LVH Yard refuses a Static Instance for a
missing Service, and it creates no Service. Adding a Static Instance does not
change the Publication, and an Unpublished Service stays Unpublished.

Each Static Instance on the Service page has an "Edit" button and a "Remove"
button. Edit opens a form with the current values. An edited Static Instance
keeps its ID. Remove deletes only that Static Instance. When you remove the last
one, the Service stays, empty.

### Container Bindings

A Container Binding binds a Service to a Docker container by its name, or to
each container of a Compose project and service. Add one on the page of the
Service, with `lvh-yard register container`, or with `lvh-yard register compose`.
A Service can have many Container Bindings.
Remove it on the page of the
Service, or with `lvh-yard remove` and `--container`, or `--compose-project`
and `--compose-service`. The Bindings are in the Consul KV of this
Workstation, under `lvh-yard/container-bindings/`.

A container or Compose service can give Instances to several Services.
Each Service keeps its own Container Bindings.

Each running container that matches is a Bound Instance with a TCP check.
Its address depends on the Scope of the Service's Publication:

| The container                         | Local Scope                                    | Yard Scope                         | No Publication                     |
| ------------------------------------- | ---------------------------------------------- | ---------------------------------- | ---------------------------------- |
| Has a Host Port on every host address | `127.0.0.1` and the Host Port                  | LAN address and the Host Port      | LAN address and the Host Port      |
| Has a Host Port on one address        | That address and the Host Port                 | That address and the Host Port     | That address and the Host Port     |
| Has no Host Port                      | The container's own address and Container Port | The same                           | The same                           |
| Is on the host network                | `127.0.0.1` and the Container Port             | LAN address and the Container Port | LAN address and the Container Port |

A container port can have several Host Port mappings, for example one on
`127.0.0.1` and one on the LAN address. The Bound Instance uses one mapping,
with its own Host Port:

- Local Scope prefers a mapping on loopback or on every address.
- Yard Scope prefers a mapping on every address, or on an address that is not
  Out of range.
- Among the preferred mappings, one on every address wins. Then the first in
  Docker's order wins.
- If no mapping is preferred, the first of all mappings is used, with the usual
  warning.
- With no Publication, no mapping is preferred.
- The container's own address is used only when the port has no mapping.

When you publish, change the Scope or unpublish, the Bound Instances change at
once. You do not restart the container. Each Service that follows a container
has its own addresses. A Bound Instance comes and goes with its container. You
cannot edit or remove it alone.

Local Scope decides who gets a Route. It does not make a Host Port private, and
it does not change the Docker binding or where the process listens. A port that
Docker publishes on every address stays open on the LAN. A process that listens
only on loopback does not accept connections at the LAN address.

A container with no Host Port needs no published port and no shared Docker
network. The Consul check, the Reachability test and Traefik reach its own
address from the Workstation, also on Windows with Docker Desktop. Containers on
unrelated Docker networks work the same way. That address is private to this
computer. The Docker ranges are in Out-of-range, so other Workstations do not
route to it. Under Yard Scope this Workstation still routes to it, and the
Service page warns that other Workstations cannot reach it.
When the container stops, its Bound Instance goes. When it starts again, the
Bound Instance returns at the container's new address.

Each source on the Instance Sources card has a box with its Instances, such as
"1 Instance, 1 passing". Open it to read each Instance's address and Health.
A thick bar at the left of each Instance sets it apart from the next one. Under
the address, the facts are on the left: container, Container Port, Host Port,
tags and meta. A Bound Instance with no Host Port says "Not published". A
Static Instance has no container fields. Its checks are on the right, each with
its Health and last output. On a phone, the checks go below the facts. The box
starts open when an Instance does not pass, and closed when every Instance
passes. It stays open or closed as you left it when the page refreshes. Remote
Services show the same facts and checks, from their Home Workstation.

Each Container Binding is one source on the Instance Sources card. Its Edit and
Remove buttons are at the right of its title, and its Bound Instances are in its box.
A Binding with no match says that no container runs. The API gives each Bound
Instance's `container` and `containerPort`. It also gives `hostPort` when the
Instance has a Host Port. A Static Instance has none of these fields.

The dashboard follows the Docker events at `DOCKER_HOST`. If that is not set,
it uses `unix:///var/run/docker.sock`. When it starts, or when Docker answers
again, it updates each Binding to the containers that run then.

#### Published ports on Windows

The table above holds on Windows. Docker Desktop publishes each port on the
Windows computer, and these limits apply:

- Docker Desktop lists a port that it publishes on every address twice, on
  `0.0.0.0` and on `::`. A Windows Workstation uses only the IPv4 mapping. A
  port published only on an IPv6 address gives no Bound Instance.
- `-p 8080:80` is open on `127.0.0.1` and on the LAN address of the Windows
  computer. Local Scope uses `127.0.0.1`. Yard Scope uses the Instance host,
  which is the saved LAN address unless `YARD_INSTANCE_HOST` says otherwise.
- `-p 127.0.0.1:8080:80` is open on the Windows computer only. A Service with
  Yard Scope keeps the Bound Instance at `127.0.0.1` and shows a warning.
  Other Workstations get no Route. To share the port, publish it on every
  address or on the LAN address.
- `-p <LAN address>:8080:80` is open at that address only. It is not open at
  `127.0.0.1`. The Bound Instance stays at the LAN address under every Scope,
  so the Route works on this Workstation and on other Workstations.
- A port with no Host Port has an address inside the Docker virtual machine,
  such as `172.17.0.7`. This Workstation reaches it, but other Workstations
  do not. Yard Scope warns and gives no Route to them. Publish the port to
  share it.
- The Bound Instance never uses `host.docker.internal`, or an address of WSL
  or of the Docker virtual machine, as the address that other Workstations
  use.
- When Docker restarts or replaces a container, the Host Port or address can
  change. The Bound Instance follows the new mapping.

A port that Docker publishes on every address, or on the LAN address, is open
on the LAN. Other Workstations reach it only when Windows Firewall lets Docker
Desktop accept their connections. LVH Yard never changes the firewall.

A program that runs on Windows itself, not in a container, is different. For
example, a dev server that you start in PowerShell may listen on every address,
but Windows Firewall still blocks other computers from reaching it. To give a
Yard Scope Publication to such a program, add an inbound rule in Windows
Firewall that allows that program or port from the Yard's addresses only. LVH
Yard never changes the firewall.

#### Bind a container on the Service page

On the page of a Service that you created, click "Bind container" in the
Container Bindings card. The form opens on its own page,
`/services/<workstation>/<service>/container-bindings/new`. Cancel goes back
to the page of the Service.

The Container field is a list that you can also type in. It lists:

1. Each Compose service, as `project/service`. A Binding to it follows every
   container of that Compose service, so it still works after
   `docker compose up` makes new containers.
2. Each container with no Compose project, by its name.

The list holds stopped containers too. Each entry shows `running` or `stopped`.
A Compose service with only some containers running shows how many, such as
`1 of 2 running`. A target that other Services follow shows `used by` and
their names. You can still select it, so one container or Compose service can
give Instances to several Services.

You can also type a target that does not exist yet. Type a container name,
or a Compose project and service as `project/service`. A Docker container
name has no `/`, so the two cannot mix.

The Container Port field is a list that you can also type in. It lists the open
ports of your pick, each marked as published on a host port or not published.
For a Compose service, it lists the ports of all its containers. You can type
any other port. Docker lists no ports for a stopped container, so then you type
the port.

Click Bind. The page of the Service opens. If the binding fails, the form
shows your input again with the error under its field.

If the Service has Yard Scope and the container port is not published, the
page of the Service warns that other Workstations cannot reach the Bound
Instance.

#### Edit a Container Binding

Click "Edit" at the right of a Container Binding's title. The form is the same as
the bind form, with the target and the port filled in. Click Save to change them.
A Binding has no other settings, so you can fix the port or the target there
without removing it. The Binding gets a new ID, because the ID holds the target and
the port, but it keeps its place in the order. Its Bound Instances follow the
new target and port at once. If the Service has another Binding with the same
target and port, the form refuses the change. The page of a Remote Service has
no Edit button.

#### The Containers page

Open Containers in the navigation bar to see this Workstation's Docker
containers. Running and Stopped have separate sections, grouped by Compose
project and service.

Two selectors above the list filter it. State shows Running, Stopped or All
containers. Followed shows containers and Compose services that still need a
Service (Not followed), only those that a Service follows (Followed), or All.
A target still needs a Service when no Service follows it, or when one of its
known ports has none. A Compose service counts as followed when any of its
containers is. The page starts with Running and Not followed. The filters stay
in the page address, and the list keeps them when it refreshes.

Every container shows on its own line, with nothing to open. A line gives the
name, the ports, who follows them and a Create Service button. A Compose
service with one container is one line: the service name, then the container
name. A Compose service with more containers shows only the service and its
container count. It hides the containers. Ports show only the Container Port,
not the Host Port or the bind address. If Docker does not answer, the page
shows the reason and keeps trying.

Each Container Port has its own line. A Service follows a port when it has a
Container Binding at that port. The line then shows Followed by, with links to
the Services, and no button. If no Service follows the port, the line shows
Create Service. For a Compose service, a Binding to the service or to any of
its containers follows that port for the whole service, in both Running and
Stopped.

A container with no known ports has one line. Docker lists no ports for a
stopped container. That line shows Followed by, for any port, and always shows
Create Service. Click Create Service to open its form. The form selects the
port of the line that you clicked. A Binding to a Compose service follows every
replica, including containers that start later.

The form creates one new Service with one Container Binding.
Its target is fixed to the container or Compose service that you opened.
You can edit the proposed Service name. An existing name is refused.
The Container Port combobox lists the detected ports and accepts a typed
port. A single known port is selected for you. Docker lists no ports for
stopped containers, so you must type one unless a running container of the
same Compose service supplies it.

Publish starts checked, with Local Scope. Choose Yard Scope to give other
Workstations a hostname, or clear Publish to leave the Service Unpublished.
You can set Route Profiles and access logging on the Service page.
Click Create Service to create the Service, add its Container Binding
and publish it if checked. Cancel returns to the container list.

The form checks all input before it writes. If a write fails, it stops
and lists the confirmed changes. Saved changes stay; nothing is rolled
back. Open the Service to check what exists before trying again.
Creating or publishing a Service never starts or stops its containers.

#### The container list

`GET /api/workstation/containers` gives every container of this Workstation,
stopped ones too. Compose projects come first. Each entry gives `name`,
`composeProject` and `composeService` when the container has both Compose
labels, and `running`. Each published TCP port mapping includes
`containerPort` and `hostPort`. The optional `hostIp` field gives the address
Docker binds on the Workstation. An unpublished port has no `hostPort` or
`hostIp`. A stopped container has no ports. `followedBy` lists the Services
that have a Container Binding that matches the container, whatever the port of
the Binding, and is empty when none does. Each port also has `followedBy`. It
lists the Services that have a Container Binding at that port of the
container's target. The target is the container, or its Compose service, so a
Binding by name to any container of the service counts for all of them. The
dashboard uses it to show Followed by or Create Service for each port. If the
dashboard cannot read Docker, the answer is 503 and says why.

`lvh-yard containers` lists the running containers of this Workstation that no
Container Binding follows. Use `--state running|stopped|all` and
`--followed no|yes|all` to change that. A Compose service counts as followed
when any of its containers is. `--state all --followed all` lists every
container. It groups them by Compose project and service. Each service line
starts with its name, then gives the container count and combined Container
Ports. When some containers are stopped, the line gives both counts.
It marks each Unfollowed Container by name: a running container that no
Container Binding matches. Standalone containers keep separate lines.
Add `--details` to list each container below its service, with mappings such
as `127.0.0.1:8080->80`. IPv6 addresses have brackets, such as `[::]:8080->80`.
If the bind address is unknown, the CLI keeps the Host Port and marks the
mapping with `(bind address unknown)`.
The detail lines also show whether each container runs and its Docker
HEALTHCHECK state when one is set.

#### The REST API

`POST /api/workstation/services/<service>/container-bindings` binds a Service.
The body gives `container`, or `composeProject` and `composeService`, and
`port`. If the Service does not exist, the binding is refused with 404. Any
number of Services can bind the same container or Compose service, on the same
port or on another one. Binding the same target and port again to the same
Service changes nothing.

From the terminal:

```sh
lvh-yard containers                                      # all containers, with Unfollowed Containers marked
lvh-yard register container shop-web-1                   # a container with one Container Port
lvh-yard register container shop-web-1 --port 9000       # choose the port of a container with several or none
lvh-yard register container shop-web-1 --service shop    # choose the Service name
lvh-yard register container shop-web-1 --port 80         # a container that does not run yet
lvh-yard register compose shop/web --port 80                # a Compose service, running or not
lvh-yard register compose shop/web --port 80 --service shop # choose the Service name
```

`lvh-yard register container <name>` follows only the named container, even
when it belongs to a Compose service. The default Service name is the cleaned
container name. If the dashboard lists exactly one Container Port for the
container, `--port` can be left out.

`lvh-yard register compose <project>/<service>` follows every container of
that Compose service. The default Service name is the cleaned Compose
service name. This command needs `--port`, whether its containers run or not.
Both commands accept `--service`. The Service must exist.
`lvh-yard register` alone lists `static`, `container` and `compose`.

### Health checks

Each Instance has one health check. A TCP check connects to its port. An HTTP
check sends a GET to a path and passes on a 2xx answer. Script checks are not
available. The page of the Service shows the last output of each check. You can
edit the host, port, check, tags and meta of a Static Instance there, and it
keeps its ID.
