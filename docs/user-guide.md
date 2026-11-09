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
Catalog, Aliases, Route Profiles and Backups pages, and it
highlights the current page.

The Catalog shows one row for each Service, grouped by Workstation. Its columns
are Service, State, Hostname and Instances. The Instances count says "2 of 3
passing", for example, or "No Instances". Unpublished Services show the count
too. Instance addresses are on the Service page. You can filter by Workstation
and state. The Catalog refreshes every five seconds and keeps your filters.

## Running on a Workstation

You need Docker with Compose and the `lvh-yard` executable. You do not need
Java or a copy of this repository.

1. Download `lvh-yard` and put it on your `PATH`, as
   [Installing the CLI](#installing-the-cli) shows.
2. Run `lvh-yard init`. It asks for the name of this Workstation and for its
   Base Domain. Press Enter to take the default: the hostname of this computer
   as the name, and `lvh.me` as the Base Domain. You can give both as flags
   instead:

   ```sh
   lvh-yard init --workstation ws-a --base-domain lvh.me
   ```

   `init` writes `compose.yaml` and `.env` to
   `~/.config/lvh-yard`. If `XDG_CONFIG_HOME` is set, it writes to
   `$XDG_CONFIG_HOME/lvh-yard`. `--dir` picks another folder. It also creates
   the empty [Backups folder](#the-backups-folder-and-the-safety-backup),
   `backups/`.

   `init` makes a new gossip key and prints it. This Workstation then starts a
   Yard of its own. To join a Yard, see [Joining a Yard](#joining-a-yard).

3. Open TCP port 8300 and TCP and UDP port 8302 to the other Workstations.
4. Start this Workstation: `cd ~/.config/lvh-yard && docker compose up -d`.
   This starts Consul, the dashboard and Traefik, in that order. When one is
   healthy, the next one starts. `docker compose ps` shows the
   health of each one. If you gave `init` another folder, `cd` to that folder.
5. Make sure that LVH Yard works: `lvh-yard status`.

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

Run the other `docker compose` commands in the same folder. For example,
`docker compose logs -f consul` shows the logs of Consul. Consul logs only
errors.

`docker compose logs -f traefik` shows one line for each request that Traefik
sends on. The line ends with the Route, the address of the Instance that
answered, and the time in milliseconds. The status of the response comes after
the request. Traefik logs only the Services that you turn on, for example with
`lvh-yard publish <service> --access-log on`.

If a request to the dashboard fails, `docker compose logs dashboard` shows one
line for it, with no stack trace. To also log the stack trace, set
`YARD_STACK_TRACES=true` in `.env`. Then run `docker compose up -d`.

If a page of the dashboard fails, the browser shows an error page. It says
what failed and links to the Status page. The dashboard talks to Consul only
through the Consul HTTP API, whatever runs Consul. If that API does not answer,
the page shows its address and a request that tests it. The REST API still
answers errors with JSON.

The files that `init` writes are a reference setup, and they are yours. You
can change `compose.yaml`, or run Consul, Traefik and the dashboard another
way. The comments in `compose.yaml` and `.env` explain each value. `lvh-yard`
only talks to the dashboard. It does not start or stop anything.

### Joining a Yard

A Workstation joins a Yard through one Peer, the LAN address of a Workstation
that is already in the Yard. It also needs the gossip key of that Yard. Copy
the key from `YARD_GOSSIP_KEY` in the `.env` of that Workstation. Then run
these commands on the new Workstation:

```sh
lvh-yard init --reinit --peers 192.168.1.10 --gossip-key <key>
cd ~/.config/lvh-yard && docker compose up -d
```

You can also give `--peers` and `--gossip-key` to the first `lvh-yard init`.

### Upgrading

`lvh-yard init` does not overwrite a file unless you give `--force`. After you
install a new `lvh-yard`, use one of these flags:

- `lvh-yard init --upgrade` changes only the consul, traefik and dashboard
  images in `compose.yaml`. Then run `docker compose up -d` again in the
  folder of this Workstation.
- `lvh-yard init --reinit` writes `compose.yaml` and `.env` again from the new
  templates, with the values of the old `.env`. A flag that you give with it
  replaces the old value. The old files stay as
  `<file>.<yyyyMMdd-HHmmss>.bak`. If the `backups/` folder is missing, it
  creates it. The new `compose.yaml` lets the dashboard write to this folder.

#### An old `route-profiles.yaml`

Older versions read the Route Profiles from `route-profiles.yaml` in the
folder of the Workstation. The new dashboard does not read this file. It reads
the profiles only from Consul KV.

The old dashboard copied each valid profile into Consul KV. So your valid
profiles stay after the upgrade, and their Routes keep their auth. A profile
that the old dashboard refused never reached Consul KV, so it is missing now.

To get back a refused profile, fix it in the old file. Then apply the file
once:

```sh
lvh-yard profiles set route-profiles.yaml
```

`lvh-yard profiles set` adds or replaces each profile under
`http.middlewares.<name>` in the file. It keeps the profiles that the file
does not name. If the dashboard refuses one profile, it saves none of them and
shows the reason. To read the file from standard input, give `-` as the file.

After the profiles are back, `lvh-yard profiles` lists them. Then you can
delete `route-profiles.yaml`. An old `YARD_ROUTE_PROFILES` line in `.env` does
nothing now, so you can delete it too.

### Addresses and ports

Traefik and the dashboard listen on `127.0.0.1`, so only this computer reaches
them. To change that, set `YARD_TRAEFIK_BIND_ADDRESS` or
`YARD_DASHBOARD_BIND_ADDRESS` to an IPv4 address. `0.0.0.0` listens on every
address, loopback too.

If Traefik does not listen on loopback, the `lvh.me` hostnames do not work on
this computer, because they resolve to `127.0.0.1`.

The dashboard has no login. Anyone who reaches it can change the Services of
this Workstation, and its containers through the Docker socket.

The admin page of Traefik is at `127.0.0.1:8080/dashboard/`, and its ping is at
`127.0.0.1:8080/ping`. They are always on loopback, whatever
`YARD_TRAEFIK_BIND_ADDRESS` says. `YARD_TRAEFIK_ADMIN_PORT` changes the port.

### The dashboard image

The folder that `lvh-yard init` writes runs the dashboard from its image,
`ghcr.io/ppalazon/lvh-yard-dashboard`. The image tag is the version of
`lvh-yard`. `init` sets these values in `.env`:

- `COMPOSE_PROFILES=dashboard`, which turns on the dashboard in Compose
- `YARD_DOCKER_GID`, the group that owns the Docker socket
  (`stat -c %g /var/run/docker.sock`)
- `YARD_UID` and `YARD_GID`, your user and group

The dashboard runs as your user, not as root. It joins the socket group so that
it can read the Docker socket. It uses host networking. On Docker Desktop, turn
on host networking in the settings.

## Installing the CLI

Each release has `lvh-yard` as one executable for Linux (amd64, arm64) and
macOS (arm64). It needs no Java. Download it from the
[releases](https://github.com/ppalazon/lvh-yard/releases). Make sure that it
matches `SHA256SUMS`, and put it on your `PATH`:

```sh
sha256sum -c SHA256SUMS --ignore-missing
tar -xzf lvh-yard-0.1.0-linux-amd64.tar.gz lvh-yard
sudo mv lvh-yard /usr/local/bin/
```

On macOS, the system blocks a downloaded executable. To unblock it, run
`xattr -d com.apple.quarantine lvh-yard`.

### Shell completion

`lvh-yard completions <shell>` prints a completion script for Bash or Zsh.
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
The script uses Zsh's Bash completion support.

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
lvh-yard catalog --details                  # Instance addresses, Health, IDs and Service Warnings
lvh-yard catalog --details --workstation ws-b --state degraded
lvh-yard service create web                 # an empty, Unpublished Service
lvh-yard service delete web                 # with its Publication, Static Instances and Container Bindings
lvh-yard register static web --port 8080    # at this Workstation's LAN address
lvh-yard register static web --host 127.0.0.1 --port 8080
lvh-yard register static web --port 8080 --path /health --interval 5s --timeout 1s   # an HTTP check (default: TCP, 10s, 2s)
lvh-yard register static web --port 8080 --tag backend --meta owner=pablo           # repeat --tag and --meta for more
lvh-yard containers                         # Compose service counts and Container Ports
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
lvh-yard unpublish web
lvh-yard priority web                       # the Instance Sources in order, with their IDs
lvh-yard priority web <id> <id>             # put these sources first (Instance Priority)
lvh-yard priority web --off                 # every passing Instance shares the Route
lvh-yard alias add grafana --workstation ws-b                 # http://grafana.<base-domain> for grafana of ws-b
lvh-yard alias add db --workstation ws-b --service postgres   # a name that differs from the Service
lvh-yard alias list
lvh-yard alias remove grafana
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

`lvh-yard catalog` shows aligned columns for the Service name, state, hostname
and Instance count. The count says "N of M passing". It says "Empty" for a
Service with no Instances and no Container Bindings, and "No Instances" for a
Service whose Container Bindings follow no running container.
Unpublished Services show the count too, but have no hostname. By default,
the command prints no Instance IDs or Service Warnings. Add `--details` to
show each Instance's address, Health and ID, followed by the Service Warnings.
Both views support `--workstation` and `--state`. You can combine the filters.
The states are `unpublished`,
`unavailable`, `unhealthy`, `degraded` and `healthy`. An unreachable Workstation
shows `Workstation unreachable` instead of its Services.

If a command fails, it prints the reason and exits with a code that is not 0.

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

`lvh-yard profiles edit <name>` opens the profile in `$EDITOR`, in the format of
`show`. If `$EDITOR` is not set, it opens `vi`. After you close the editor, the
command saves the profile. If you did not change the text, it saves nothing.
The file must hold only that one profile, with the same name. If LVH Yard
refuses your change, the command prints the reason and asks
`Edit again? [Y/n]`. Press Enter to open your text again. Answer `n` to stop:
the command then prints the path of the file that holds your text.

The `/status` page of the dashboard and `GET /api/status` show the same status
as `lvh-yard status`.

## Backups

A Backup is one YAML file that holds what you set up on one Workstation:

- each Service of this Workstation, with its Publication, Static Instances and
  Container Bindings. LVH Yard's own Services, such as `yard`, are in it too.
- the Aliases of this Workstation
- the Route Profiles of this Workstation

A Backup never holds Bound Instances, Health or Remote Services. A Container
Binding gives its Bound Instances again when its containers run.

`lvh-yard export` prints the Backup. `lvh-yard export -o <file>` writes it to a
file. The API gives the same Backup at `GET /api/workstation/backup`, as
`application/yaml`:

```sh
curl -H 'Accept: application/yaml' http://127.0.0.1:7780/api/workstation/backup
```

A Backup looks like this:

```yaml
version: 1
exported-from:
  workstation: ws-a
  at: "2026-11-05T10:00:00+01:00"
route-profiles:
  team-login:
    basicAuth:
      users:
        - ana:$2y$05$abc
services:
  grafana:
    publication:
      scope: yard
      route-profiles:
        - team-login
      access-log: true
    static-instances:
      - port: 3000
        check:
          type: http
          path: /health
          interval: 10s
          timeout: 2s
        tags:
          - dashboards
  shop:
    publication: none
    container-bindings:
      - container: shop-web-1
        port: 80
      - compose:
          project: shop
          service: worker
        port: 9000
aliases:
  db:
    workstation: ws-b
    service: postgres
```

- `publication: none` means that the Service is Unpublished.
- `access-log: true` means that Traefik logs the requests to the Service. If
  the Backup leaves it out, Traefik does not log them.
- If the host of a Static Instance is the LAN address of this Workstation, the
  Backup leaves the host out. So the Static Instance fits another computer.
- The Backup leaves out empty tags and meta.

A Backup holds the basic-auth hashes of your Route Profiles. Anyone who can
read the file can read them. Keep a Backup as private as a password file.
`lvh-yard export -o` writes the file so that only you can read it.

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

- a Service, an Alias and a Route Profile by their names
- a Static Instance by its host and port, inside its Service only
- a Container Binding by its ID, such as `shop-web-1:80` or
  `compose:shop:worker:9000`

With `--update`, the import overwrites what it finds:

- the Publication of a Service: its Scope and Route Profiles
- the health check, tags and meta of a Static Instance. The Instance keeps its
  ID.
- the target of an Alias
- the definition of a Route Profile

A Container Binding has nothing more to change, so the import always skips it.
An entry with the same values is a skip in both modes.

A Static Instance without a host is at the LAN address of this Workstation. A
new Service without a `publication` key stays Unpublished. An existing Service
without a `publication` key keeps its Publication, in both modes. With
`--update`, `publication: none` unpublishes an existing Service. A Publication
can name a Route Profile of the same file.

Before the import changes anything, the dashboard checks the whole file. If one
entry is wrong, the import changes nothing, prints each problem with its place
in the file, and exits with 1:

```text
services.grafana.static-instances[0].port: The port 70000 is not a TCP port: use 1 to 65535.
services.grafana.publication.route-profiles[0]: The Route Profile login is not in the file, and this Workstation does not have it.
```

The import refuses a key that a Backup does not have, a version other than 1,
and an unknown Route Profile. It also refuses an Alias to this Workstation, and
a Container Binding to a container that another Service follows already.

The import writes Route Profiles first, then Static Instances and Container
Bindings, then Publications, then Aliases. Consul cannot write them all at
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
`~/.config/lvh-yard/backups`. `lvh-yard init` creates it. `compose.yaml` lets
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
holds. If a file is not a Backup, its row shows why instead.

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

1. Click **Import** in its row. A file that is not a Backup has no **Import**.
2. Choose the mode. **Add** adds what is new and skips what exists. **Update**
   also overwrites what exists with the values of the file.
3. Click **Show the plan**. The page shows the plan, as a dry run. Nothing
   changes yet. If the file has problems, the page shows each one, and you
   cannot import it.
4. To import, click **Import**. The page shows what the import did and the name
   of the safety Backup. To go back, import that safety Backup in update mode.

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
`lvh-yard service create <name>`. A name that this Workstation already uses is
refused. The catalog marks a Service with no Instances and no Container
Bindings as Empty.

Static Instances and Container Bindings attach to a Service that exists. A
Static Instance or a Container Binding for a missing Service is refused, and
so is a Publication. The Services are in the Consul KV of this Workstation,
under `lvh-yard/services/`.

To delete a Service, click "Delete Service" at the bottom of its page. A
confirmation page names what goes: the Publication, the Static Instances, and
the Container Bindings with their Bound Instances. Click Delete. The containers
keep running. You can also run `lvh-yard service delete <name>`.

A Service that only Foreign Instances give shows without being created. It
stays while they are registered, also after a delete, and its page says why.
To publish it, create a Service of the same name.

A Service is `unpublished`, `unavailable` (published, no Instances), `unhealthy`
(published, no passing Instance), `degraded` (published, some Instances pass and
some do not) or `healthy` (published, every Instance passes). Warning and critical
Health both count as not passing. The state counts every Instance, whatever the
Instance Priority or its Out-of-range mark. Degraded and warning Health have orange
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
each request to the Route. An Alias follows the Service it names. If you publish
again without `--access-log`, the setting does not change.

You can see a Remote Service, but only its Home Workstation can change it. Its
page has no forms, and the API answers 405 to a change under `/api/services`.

### Service Warnings

A Service Warning is a problem that the setup of a Service causes. Each one has
one of two levels:

- **error**: the hostname does not work. An unknown Route Profile is an error.
- **risk**: the hostname works, but other Workstations or users can meet a
  problem. An Instance on loopback, a container port that is not published, a
  Route Profile that asks who the user is, and a Foreign Instance are risks.

A Foreign Instance is an Instance that another tool registered straight in
Consul, for example with `curl` or the `consul` command. LVH Yard shows it and
routes it as a Static Instance. LVH Yard cannot edit or remove it, so its row
has no Edit or Remove button. A Backup does not hold it. To remove it,
deregister it from the Consul agent of its Home Workstation. For example, on
that Workstation, send this request to the Consul HTTP API. The port is the
value of `YARD_CONSUL_HTTP_PORT`, 8500 by default:

```text
curl -X PUT http://127.0.0.1:8500/v1/agent/service/deregister/<instance-id>
```

An unpublished Service also shows this risk.

The API sends each Service Warning in `warnings`, with its `kind`, its
`severity` (`error` or `risk`) and its `message`. The Service state is not a
Service Warning, so an Unavailable or Unhealthy Service has no Service Warning
for its state.

`lvh-yard catalog --details`, `lvh-yard publish` and
`lvh-yard register container` print the level before each message:

```text
    error: The Route Profile team-login does not exist on the Home Workstation, ...
    risk: The Instance at 127.0.0.1:8080 is on loopback, so other Workstations cannot reach it. ...
```

The Service page shows what stops the hostname from working as red alerts under
the title, one alert for each problem. Each error is one alert. An Unavailable
Service also shows "No Instances are registered", and an Unhealthy Service
shows "No Instance passes its checks".

Under the red alerts, one closed box holds the risks, for example "2 risks to
keep in mind". Click it to read them. The page refreshes every 5 seconds, and
the box stays open or closed as you left it. If you leave the page, the box is
closed again on your next visit. A Service without risks has no box.

Under the alerts, a row of cards shows the Service at a glance:

- State: Healthy, Degraded, Unhealthy, Unavailable or Unpublished.
- Publication: the hostname, the Scope and the access log, or "Not published".
- Passing Instances: the Instances that pass their checks, out of all
  Instances, for example "1 of 2".
- Routes to: the Instance Source that the Route uses now. See
  [Instance Priority](#instance-priority).

Under them are the Publication, Instance Sources and Route Profiles cards.
Each card holds the buttons that change it. On a wide screen the Publication
and Route Profiles cards sit in two columns, and on a phone in one. The
Instance Sources card and the Reachability test are at full width. The page of a Remote Service
shows the same cards and alerts, without the buttons that change it.

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
Route. A new Foreign Instance goes to the end too. An edited Static Instance
keeps its place. A removed source leaves the order. If you add it again, it
goes to the end.

If no Instance passes its check, the Route uses every Instance in range of
every source. Out-of-range Instances never count.

An Alias source works only on your own Workstation. Other Workstations skip
it, and use the next source in the order. Apart from that, every Workstation
that routes a Service with Yard Scope uses the order of its Home Workstation.

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
Route uses every Instance in range. The order stays. When you turn the
Instance Priority on again, the order is back:

```sh
lvh-yard priority web --off
lvh-yard priority web --on
```

The order and the toggle are in the record of the Service, not in its
Publication. When you unpublish and publish again, they stay.

On the page of the Service, the Instance Sources card lists the sources in
order, each with its Instances. The Up and Down buttons change the order. They
hide while the Instance Priority is off. The toggle button turns it off and
on. The source that the Route uses now has an "In use" mark. The page of a
Remote Service shows its sources in order, without the buttons.

The "Routes to" card of the Service page names the source in use, for example
"Static Instance 10.1.2.3:3000", and how many sources wait. While the Instance
Priority is off, it shows "Every passing Instance" and "Instance Priority off".
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

A Reachability test tells whether this Workstation reaches a Service. To run
one, press Test on the page of the Service, run `lvh-yard test`, or send
`GET /api/services/<workstation>/<service>/reachability`.

The test connects to each Instance directly. It also sends a request for each
hostname of the Service, and of its active Aliases, through the local Traefik.
If the Service has no Route here, only the Instances are tested, and the result
says why. An Out-of-range Instance is skipped, and that is not a failure.

The result also says which Instances the Route uses now, under the Instance
Priority. So an Instance that is reached but not in the Route is left out by
the order, not broken. When the Route falls back to an Alias, the result names
the Remote Service that gets the requests. While the Instance Priority is off, the result
says so.

If Traefik has no Route for the hostname, or answers 502, 503 or 504, the
request through Traefik fails. Any other answer means that the request reached
the Service. The results are not stored. If a test fails, `lvh-yard test` exits
with 1.

### Aliases

An Alias gives a Remote Service a short hostname on this Workstation only, for
example `grafana.<base-domain>` for `grafana.ws-b.<base-domain>`. Add and
remove Aliases on the Aliases page or with `lvh-yard alias`. The Aliases are in
the Consul KV of this Workstation, under `lvh-yard/aliases/`.

To add an Alias on the Aliases page, click "Add an Alias". The form opens on
its own page, `/aliases/new`. Cancel goes back to the Aliases page. If a value
is wrong, the form shows your input again with the error under its field.
If no Remote Service has Yard Scope, the page shows no link.

The name of an Alias follows the rules for a Service name, so it cannot be
`consul`. The Workstation and the Service that it names must each be a DNS
label. LVH Yard does not ask the other Workstation when you add an Alias. So
you can add an Alias while that Workstation is off, or before it has the
Service. The Alias gets a Route only when its Workstation answers and has the
Service with Yard Scope.
These are the states of an Alias:

- `active`: routed to the Instances of the Remote Service
- `unavailable`: the Remote Service has no Instance in range here
- `fallback`: a published Service of this Workstation has the same name. The
  Alias is one of its Instance Sources, and its Instance Priority says when the
  Route uses it. The reason says if the Alias is in use now.
- `not-shared`: the Remote Service is unpublished or has Local Scope
- `unreachable`: its Workstation does not answer
- `missing`: the Remote Service or its Workstation is gone

### Route Profiles

A Route Profile is a named set of Route options, such as basic auth or added
headers. LVH Yard gives it to Traefik as a middleware.
The Consul KV of this Workstation holds its Route Profiles, under
`lvh-yard/route-profiles/<name>`. Other Workstations read the profiles there.
A profile is one middleware in the format of Traefik, such as
`{"headers": {"customRequestHeaders": {"X-Forwarded-Proto": "https"}}}`.

The Route Profiles page of the dashboard lists each profile with its name, its
Traefik type and the Services whose Publications name it.

- To add a profile, type its name, and write its middleware as YAML in the text
  box, such as `headers:` with its options below it. Then click Save.
- To start from an example, choose one and click New profile. The examples are
  basic auth, an IP allow list, headers, CORS and a chain. Change the example
  before you save it: the basic auth example has no real password hash.
- To change a profile, click Change in its row. The form then shows its YAML.
  A save with the same name replaces the profile.
- To remove a profile, click Remove in its row. If a Publication or a chain
  names it, the page refuses and names each Service and chain.

If the dashboard refuses a save, the page shows the reason and keeps your text
in the form. The Service page links each Route Profile of a Publication to its
row on this page.

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

A profile must work on every Workstation, so the dashboard refuses a profile
that:

- uses a plugin
- reads its users from a file (`usersFile`). Write them under `users`, as
  hashes from `htpasswd -nbB <user> <password>`.
- sends `forwardAuth` to `localhost` or to an out-of-range address
- is a chain that names a middleware that is not a Route Profile of this
  Workstation, or that is refused
- has a type that is not a Traefik middleware, such as a typo
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

The Route gets each profile as a Traefik middleware named
`<workstation>_<profile>`, such as `ws-a_basic-auth`, in the order of the
Publication. A chain's members get the same name in front. So the profiles of
two Workstations never clash.

Before you protect a Service with Yard Scope, check that every Workstation in
the Yard runs a version of LVH Yard with Route Profiles. An older version does
not read the profiles of a Publication, so it routes the Service without them.

A Service with Yard Scope gets its profiles on every Workstation. A teammate's
Route to it, and a teammate's Alias to it, apply the same profiles. Their
dashboard reads the profiles from your Consul KV, so nobody copies a file. When
you change a profile, their Routes change within a few seconds. The Service's
page on their dashboard shows the profile names.

A profile with `basicAuth`, `digestAuth` or `forwardAuth`, also inside a chain,
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
host, the port, the health check, the tags and the meta. If you leave the host
empty, the Instance is at the LAN address of this Workstation. Cancel goes back
to the Service page. If a value is wrong, the form shows your input again with
the error under its field. You can also run `lvh-yard register static`.

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

One container name, or one Compose project and service, has at most one
Container Binding on a Workstation. A second one is refused, whatever its port,
and the refusal names the Service of the first one.

Each running container that matches is a Bound Instance with a TCP check.
When a Container Port has a Host Port, the Bound Instance uses that Host Port.
For a port published on every host address, it uses the LAN address.
For a port published on one address, it uses that address.
A container on the host network uses the LAN address and its Container Port as
the Host Port. With no Host Port, the Bound Instance uses the container's own
address and Container Port. A Bound Instance comes and goes with its container.
You cannot edit or remove it alone.

The Service page's Instances card shows Instance, Container, Container Port,
Host Port and Health. Checks appear under Health. A Bound Instance with no Host
Port says "Not published". A Static Instance has empty container cells.
Remote Services show the same columns, from their Home Workstation.

The Container Bindings card of the Service page lists each Binding. Under
Bound Instances, it gives a count such as "3 containers, 2 passing", then
each container that the Binding follows now. A Binding with no match says that no
container runs. The API gives each Bound Instance's `container` and
`containerPort`. It also gives `hostPort` when the Instance has a Host Port.
A Static Instance has none of these fields.

The dashboard follows the Docker events at `DOCKER_HOST`. If that is not set,
it uses `unix:///var/run/docker.sock`. When it starts, or when Docker answers
again, it updates each Binding to the containers that run then.

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

The list holds stopped containers too. A target that another Container
Binding follows shows the Service of that Binding, and you cannot select it.

You can also type a target that does not exist yet. Type a container name,
or a Compose project and service as `project/service`. A Docker container
name has no `/`, so the two cannot mix.

The Container Port field lists the Container Ports of your pick. You can also
type any other port. Docker lists no ports for a stopped container, so then
you type the port.

Click Bind. The page of the Service opens. If the binding fails, the form
shows your input again with the error under its field. A typed target that
another Container Binding follows is refused at the Container field, and the
error names the Service of that Binding.

If the Service has Yard Scope and the container port is not published, the
page of the Service warns that other Workstations cannot reach the Bound
Instance.

#### The container list

`GET /api/workstation/containers` gives every container of this Workstation,
stopped ones too. Compose projects come first. Each entry gives `name`,
`composeProject` and `composeService` when the container has both Compose
labels, and `running`. Each published TCP port mapping includes
`containerPort` and `hostPort`. The optional `hostIp` field gives the address
Docker binds on the Workstation. An unpublished port has no `hostPort` or
`hostIp`. A stopped container has no ports. `followedBy` names the Service of
a Container Binding that matches the container, whatever the port of the
Binding. If the dashboard cannot read Docker, the answer is 503 and says why.

`lvh-yard containers` lists the Unfollowed Containers: the running containers
that no Container Binding matches. It groups them by Compose project and
service. Each service line starts with its name, then gives the container
count and combined Container Ports. Standalone containers keep separate lines.
Add `--details` to list each container below its service, with mappings such
as `127.0.0.1:8080->80`. IPv6 addresses have brackets, such as `[::]:8080->80`.
If the bind address is unknown, the CLI keeps the Host Port and marks the
mapping with `(bind address unknown)`.
The detail lines also show the Docker HEALTHCHECK state when one is set.

#### The REST API

`POST /api/workstation/services/<service>/container-bindings` binds a Service.
The body gives `container`, or `composeProject` and `composeService`, and
`port`. If the Service does not exist, the binding is refused with 404. If a
Container Binding of this Workstation already follows the same container
name, or the same Compose project and service, the binding is refused with
409, and names the Service of that Container Binding. This is also true for
the same target and port again.

From the terminal:

```sh
lvh-yard containers                                      # the Unfollowed Containers
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
