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

## Running on a Workstation

You need Docker with Compose and the `lvh-yard` executable. You do not need
Java or a copy of this repository.

1. Download `lvh-yard` and put it on your `PATH`, as
   [Installing the CLI](#installing-the-cli) shows.
2. Run `lvh-yard init`. It asks for the name of this Workstation and for its
   Peers, the LAN addresses of the other Workstations. You can give both as
   flags instead:

   ```sh
   lvh-yard init --workstation ws-a                                          # the first Workstation of a Yard
   lvh-yard init --workstation ws-b --peers 192.168.1.10 --gossip-key <key>  # each next one
   ```

   `init` writes `compose.yaml`, `.env` and `route-profiles.yaml` to
   `~/.config/lvh-yard`. If `XDG_CONFIG_HOME` is set, it writes to
   `$XDG_CONFIG_HOME/lvh-yard`. `--dir` picks another folder.

   On the first Workstation, `init` makes the gossip key of the Yard and prints
   it. Give the same key to each next Workstation with `--gossip-key`.

3. Open TCP port 8300 and TCP and UDP port 8302 to the other Workstations.
4. Start this Workstation: `cd ~/.config/lvh-yard && docker compose up -d`.
   This starts Consul, Traefik and the dashboard.
5. Make sure that the Core Services work: `lvh-yard status`.

If Traefik works, open [yard.lvh.me](http://yard.lvh.me). If Traefik is down,
open `127.0.0.1:7780`. The Catalog shows the Core Services: `yard` (the
dashboard), `consul` ([consul.lvh.me](http://consul.lvh.me)) and `traefik`
([traefik.lvh.me](http://traefik.lvh.me)).

### Upgrading

`lvh-yard init` does not overwrite a file unless you give `--force`. After you
install a new `lvh-yard`, use one of these flags:

- `lvh-yard init --upgrade` changes only the consul, traefik and dashboard
  images in `compose.yaml`. Then run `docker compose up -d` again.
- `lvh-yard init --reinit` writes `compose.yaml` and `.env` again from the new
  templates, with the values of the old `.env`. A flag that you give with it
  replaces the old value. The old files stay as
  `<file>.<yyyyMMdd-HHmmss>.bak`. `route-profiles.yaml` does not change.

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
lvh-yard list                               # every Service, by Workstation
lvh-yard list --workstation ws-b            # the Services of ws-b
lvh-yard register web --port 8080           # at this Workstation's LAN address
lvh-yard register web --host 127.0.0.1 --port 8080
lvh-yard register web --port 8080 --path /health --interval 5s --timeout 1s   # an HTTP check (default: TCP, 10s, 2s)
lvh-yard register web --port 8080 --tag backend --meta owner=pablo           # repeat --tag and --meta for more
lvh-yard register web --container web-1 --port 80                            # bind to the container web-1
lvh-yard register web --compose-project shop --compose-service web --port 80 # bind to the Compose service web
lvh-yard containers                         # running containers that no Container Binding follows
lvh-yard import shop-web-1                  # bind a container to a Service and publish it
lvh-yard import shop-web-1 --port 9000 --name shop --no-publish
lvh-yard remove web --port 8080             # add --host if several Instances share the port
lvh-yard remove web --id <id>               # the ID that lvh-yard list shows
lvh-yard remove web --container web-1       # add --port if web-1 is bound on several ports
lvh-yard remove web --compose-project shop --compose-service web
lvh-yard publish web                        # http://web.<base-domain> (Local Scope)
lvh-yard publish web --scope yard           # also on every other Workstation (Yard Scope)
lvh-yard publish web --priority bound,static  # prefer Bound Instances (Instance Priority)
lvh-yard publish web --priority off           # Static and Bound Instances share the Route
lvh-yard unpublish web
lvh-yard alias add grafana --workstation ws-b                 # http://grafana.<base-domain> for grafana of ws-b
lvh-yard alias add db --workstation ws-b --service postgres   # a name that differs from the Service
lvh-yard alias list
lvh-yard alias remove grafana
lvh-yard test web                           # tests whether this Workstation reaches web
lvh-yard test grafana --workstation ws-b    # a Remote Service
lvh-yard status                             # whether the Core Services work
lvh-yard status --strict                    # also exits with 1 on a warning
```

If a command fails, it prints the reason and exits with a code that is not 0.
The `/status` page of the dashboard and `GET /api/status` show the same status
as `lvh-yard status`.

## How it works

### Services and Publications

A Service is `unpublished`, `unavailable` (published, no Instances), `unhealthy`
(published, no passing Instance) or `healthy` (published, with a passing
Instance). A Publication covers every Instance of the Service, also the ones
that you register later. The Publications are in the Consul KV of this
Workstation, under `lvh-yard/publications/`.

A new Publication has Local Scope: only this Workstation routes it, at
`<service>.<base-domain>`. With Yard Scope, the other Workstations also route
it, at `<service>.<workstation>.<base-domain>`. If you publish again without
`--scope`, the Scope does not change.

A Service with Yard Scope shows a warning for each Instance on loopback, because
other Workstations cannot reach it. The page, the API and `lvh-yard publish`
show this warning.

You can see a Remote Service, but only its Home Workstation can change it. Its
page has no forms, and the API answers 405 to a change under `/api/services`.

### Instance Priority

A Route prefers some Instances over others, by their kind. A Static Instance
comes first, then a Bound Instance. The Route uses only the first kind that has
an Instance with a passing check, and only those passing Instances. For
example, `web` runs in Docker and you also start it in a terminal as a Static
Instance. While the terminal copy passes its check, `web.<base-domain>` reaches
only that copy. When you stop it, its check turns critical, and the Route moves
to the Docker copy. This takes up to about 15 seconds.

An Alias with the same name as the Service comes last. For example, you have an
Alias `web` to the `web` of a teammate. If none of your own `web` Instances
passes its check, `web.<base-domain>` reaches your teammate's `web`. Only the
passing Instances of your teammate's Service are used. This works only on your
own Workstation, for your short hostname. Other Workstations never use your
Aliases.

If no Instance passes its check, the Route uses every one of your own
Instances. Every Workstation makes the same choice for a Service
with Yard Scope. Each one leaves out its Out-of-range Instances first.

To change the order for one Service, give the kinds in the order you want:

```sh
lvh-yard publish web --priority bound,static   # prefer the Docker copy
```

The kinds are `static`, `bound` and `alias`. Each kind can appear only once.
You can leave a kind out, and the Route never prefers it. For example,
`--priority static,bound` never falls back to an Alias, and the Alias shows as
`hidden`. If you publish again without `--priority`, the order does not change.
The order stays with the Publication, so it is kept when the last Instance of a
kind goes away. Every Workstation that routes the Service uses the order of its
Home Workstation. A Publication with no order of its own uses the default
order, `static,bound,alias`.

You can also turn the Instance Priority off for one Service:

```sh
lvh-yard publish web --priority off
```

While it is off, the passing Static and Bound Instances share the Route. An
Instance whose check turns critical leaves the Route. If no Instance passes,
the Route uses every Instance in range. An Alias with the same name is never
used, and it shows as `hidden`. If you publish again without `--priority`, it
stays off. To turn it on again, give an order with `--priority`.

On the page of the Service, the Instance Priority section changes the order
with the Up, Down, Remove and Add buttons. The Turn off button turns it off,
and the Use an order button turns it on again with the default order. The API
takes the order as `priority` in the body of
`PUT /api/workstation/services/{service}/publication`. An empty `priority`,
`[]`, turns it off. A Core Service always uses the default order.

The page of the Service shows the kind in use, for example
`Routing to: Static (Bound waiting)`. While the Instance Priority is off, it
shows `Routing to: every passing Instance (Instance Priority off)`.
`lvh-yard list` marks each Instance that the Route uses with `routed`. The API shows the order in `priority` and the
Instances in use in `routing`. While the Instance Priority is off, `routing`
has `"off": true`.

### Out-of-range Instances

An address on loopback or on a Docker bridge network (172.17.0.0/16 to
172.31.0.0/16) is out of range. If an Instance of a Remote Service has such an
address, it is an Out-of-range Instance here. A Route to it reaches this
computer instead. So no Route points to it, and the API (`outOfRange`), the
page and `lvh-yard list` mark it.

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

The name of an Alias follows the rules for a Service name. It cannot be the
name of a Core Service. The Remote Service needs Yard Scope before the Alias
gets a Route. These are the states of an Alias:

- `active`: routed to the Instances of the Remote Service
- `unavailable`: the Remote Service has no Instance in range here
- `fallback`: a published Service of this Workstation has the same name. Its
  Route uses the Alias only when none of its own Instances passes. The reason
  says if the Alias is in use now.
- `hidden`: a published Service of this Workstation has the same name, and its
  Instance Priority leaves out the Alias or is off
- `not-shared`: the Remote Service is unpublished or has Local Scope
- `unreachable`: its Workstation does not answer
- `missing`: the Remote Service or its Workstation is gone

### Container Bindings

A Container Binding binds a Service to a Docker container by its name, or to
each container of a Compose project and service. Add one on the Register page
or with `lvh-yard register --container` (or `--compose-project` and
`--compose-service`). Remove it on the page of the Service or with
`lvh-yard remove` and the same options. The Bindings are in the Consul KV of
this Workstation, under `lvh-yard/container-bindings/`.

Each running container that matches is a Bound Instance with a TCP check. If
the container port is published on every host address, the Bound Instance is at
the LAN address and the host port. Otherwise, it is at the address of the
container and the container port. A Bound Instance comes and goes with its
container, and you cannot edit or remove it alone.

The dashboard follows the Docker events at `DOCKER_HOST`. If that is not set,
it uses `unix:///var/run/docker.sock`. When it starts, or when Docker answers
again, it updates each Binding to the containers that run then.

The Containers page (`/containers`, linked from the Catalog) lists the running
containers that no Container Binding follows yet. It groups Compose containers
by Compose project, and lists the other containers apart. For each container,
it shows the TCP ports, with the host port when the port is published, and the
HEALTHCHECK state when the container has a HEALTHCHECK. A container that any
Binding matches is not listed, whatever the port of the Binding. The list
refreshes every 5 seconds. If the dashboard cannot read Docker, the page says
why. `GET /api/workstation/containers` gives the same list.

To give a container with one port a hostname, click Import in its row. The
import adds a Container Binding on that port. A Compose container is bound by
its Compose project and service, so the Binding still works after
`docker compose up` makes a new container. Another container is bound by its
name. The Service gets the name of the Compose service, or of the container.
If that name is not a single DNS label, the import uses a cleaned name: lower
case, with `_`, `.` and other characters changed to `-`. If the Service is new,
the import publishes it with Local Scope, and the page of the Service opens. If
the Service already exists, it keeps its Publication and Scope, or stays
unpublished. If the import fails, for example because the name is the name of a
Core Service, the page shows why next to the container.

To choose another Service name, click Choose name. If a container has several
ports or none, click Choose port. A form opens with the proposed name and the
ports of the container. Change them, and click Import. If the import fails, the
form shows why next to the field.

If the Service has Yard Scope and the container port is not published, the
page of the Service warns that other Workstations cannot reach the Bound
Instance.

`POST /api/workstation/containers/<container>/import` does the same.
The body can give `port`, `service` (another Service name) and
`publish: false` (to bind without publishing a new Service). Give `port` when
the container does not have exactly one TCP port. If you do not, the error
lists the ports of the container.

From the terminal:

```sh
lvh-yard containers                          # the running containers that LVH Yard does not follow
lvh-yard import shop-web-1                   # a container with one port
lvh-yard import shop-web-1 --port 9000       # choose the port of a container with several or none
lvh-yard import shop-web-1 --name shop       # choose the Service name
lvh-yard import shop-web-1 --no-publish      # bind without publishing a new Service
```

### Health checks

Each Instance has one health check. A TCP check connects to its port. An HTTP
check sends a GET to a path and passes on a 2xx answer. Script checks are not
available. The page of the Service shows the last output of each check. You can
edit the host, port, check, tags and meta of a Static Instance there, and it
keeps its ID.
