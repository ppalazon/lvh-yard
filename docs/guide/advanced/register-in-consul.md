# Register a service in Consul directly

Another tool can register an Instance straight in Consul. LVH Yard shows it as a Foreign Instance and routes it like a Static Instance. It cannot change or remove it, and a Backup does not hold it. [How the Workstation Folder wires Consul, Traefik and the dashboard](compose-consul-traefik.md) shows where Consul fits.

## Register directly on Consul

Use the HTTP API of Consul on the Workstation, or the `consul` command in its container. The port is `YARD_CONSUL_HTTP_PORT`, 8500 by default:

```sh
curl -X PUT http://127.0.0.1:8500/v1/agent/service/register -d '{"ID": "foreign-1", "Name": "foreign", "Address": "127.0.0.1", "Port": 4000, "Check": {"TCP": "127.0.0.1:4000", "Interval": "10s"}}'
docker exec lvh-yard-consul-1 consul services register -id=foreign-2 -name=foreign2 -address=127.0.0.1 -port=4000
```

```text
Registered service: foreign2
```

`docker exec` needs the name of the Consul container. `lvh-yard ws compose start` runs Docker Compose in the Workstation Folder, and Compose names the project after that folder. The container is `<project>-consul-1`. The default folder is `~/.config/lvh-yard`, so the container is `lvh-yard-consul-1`. Run `docker ps` to read the name of yours.

## Catalog response and warnings

`lvh-yard catalog` lists the Service as `unpublished` until you publish it. `--details` shows the Instance, its Health and a risk:

```sh
lvh-yard catalog --details
```

```text
  foreign: unpublished
    Instance Priority: on
      1. foreign-1  Foreign Instance 127.0.0.1:4000
    127.0.0.1:4000  passing   foreign-1
    risk: [Foreign Instance] The Instance foreign-1 at 127.0.0.1:4000 is registered by another tool. LVH Yard cannot change it. To remove it, send PUT /v1/agent/service/deregister/foreign-1 to the Consul agent of its Home Workstation.
```

After `lvh-yard publish foreign`, the Route works, and the catalog marks the source `[foreign]`.

## Remove a Foreign Instance

`lvh-yard remove` cannot remove a Foreign Instance. It removes the Static Instances and the Container Bindings that LVH Yard keeps in the Consul KV of the Workstation. A Foreign Instance is an entry in the Consul agent. `remove` finds no Static Instance with that ID, and says so:

```sh
lvh-yard remove foreign --id foreign-1
```

```text
The Service foreign has no Static Instance foreign-1 on this Workstation.
```

Deregister the Instance in the Consul agent that holds it, on its Home Workstation. Use the ID from `lvh-yard catalog --details`, here `foreign-1`. The port is `YARD_CONSUL_HTTP_PORT`:

```sh
curl -X PUT http://127.0.0.1:8500/v1/agent/service/deregister/foreign-1
```

The Instance leaves the Service. A published Service stays, as `unavailable` and `Empty`. Delete it with `lvh-yard service delete foreign`.
