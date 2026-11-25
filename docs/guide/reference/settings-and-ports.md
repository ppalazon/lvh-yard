# Settings and ports

A Workstation keeps its settings in the `.env` file of its Workstation Folder. `lvh-yard ws show` prints them and hides the Gossip Key.

## Change a setting

```sh
lvh-yard ws set http-port 8080
```

```text
Saved .env.
Next:
  lvh-yard ws --dir '...' compose recreate
```

`ws set` checks the value and changes one line of `.env`. It restarts nothing. Run the printed `compose recreate` command to apply the change. A wrong value fails:

```text
invalid http-port: use a port from 1 to 65535
```

## Settings

| Name for `ws set`        | Variable in `.env`            | Default                                                                     | Accepts                                                             |
| ------------------------ | ----------------------------- | --------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| `base-domain`            | `YARD_BASE_DOMAIN`            | The Base Domain that you gave to `ws init`                                  | A domain name, such as `lvh.me`                                     |
| `peers`                  | `YARD_PEERS`                  | Empty                                                                       | IPv4 addresses with an optional port, separated by spaces or commas |
| `bind-address`           | `YARD_BIND_ADDRESS`           | `127.0.0.1`. `ws yard init` and `ws yard join` set the LAN address          | An IPv4 address, or empty for the address of the default route      |
| `instance-host`          | `YARD_INSTANCE_HOST`          | Empty                                                                       | Windows only. An IPv4 address, or empty to use `bind-address`       |
| `traefik-bind-address`   | `YARD_TRAEFIK_BIND_ADDRESS`   | `127.0.0.1`                                                                 | An IPv4 address                                                     |
| `dashboard-bind-address` | `YARD_DASHBOARD_BIND_ADDRESS` | `127.0.0.1`                                                                 | An IPv4 address                                                     |
| `traefik`                | `YARD_TRAEFIK_ENABLED`        | `on`                                                                        | `on` or `off`                                                       |
| `out-of-range`           | `YARD_OUT_OF_RANGE`           | Loopback and the Docker bridge networks, `172.17.0.0/16` to `172.31.0.0/16` | CIDR ranges separated by commas, or empty for no range              |
| `stack-traces`           | `YARD_STACK_TRACES`           | `false`                                                                     | `true` or `false`                                                   |

The ports have their own table below. Other commands change the rest of `.env`:

- `ws init --workstation` sets the name, `YARD_WORKSTATION`.
- `ws yard` sets the Gossip Key, `YARD_GOSSIP_KEY`.
- `ws tls client` and `ws tls mtls` set `YARD_CLIENT_AUTH_ENABLED` and `YARD_MTLS_ENABLED`.
- `ws init` sets `YARD_UID`, `YARD_GID`, `YARD_DOCKER_GID` and `COMPOSE_PROFILES`.

## Ports

Each port has a setting. Change it with `lvh-yard ws set <name> <port>`.

| Name for `ws set`      | Default                                | Protocol    | Used for                               | Listens on               |
| ---------------------- | -------------------------------------- | ----------- | -------------------------------------- | ------------------------ |
| `http-port`            | 80                                     | TCP         | Traefik, the hostnames over HTTP       | `traefik-bind-address`   |
| `https-port`           | Empty. 443 after `ws tls https create` | TCP         | Traefik, the hostnames over HTTPS      | `traefik-bind-address`   |
| `traefik-admin-port`   | 7781                                   | TCP         | The admin page and the ping of Traefik | `127.0.0.1`              |
| `dashboard-port`       | 7780                                   | TCP         | The dashboard                          | `dashboard-bind-address` |
| `consul-http-port`     | 8500                                   | TCP         | The HTTP API of Consul                 | `127.0.0.1`              |
| `consul-dns-port`      | 8600                                   | TCP and UDP | The DNS of Consul                      | `127.0.0.1`              |
| `consul-grpc-tls-port` | 8503                                   | TCP         | The gRPC of Consul, with TLS           | `127.0.0.1`              |
| `consul-server-port`   | 8300                                   | TCP         | The RPC between Workstations           | `bind-address`           |
| `consul-serf-lan-port` | 8301                                   | TCP and UDP | The gossip inside one Workstation      | `bind-address`           |
| `consul-serf-wan-port` | 8302                                   | TCP and UDP | The gossip between Workstations        | `bind-address`           |

## What faces the LAN

A new Workstation listens on `127.0.0.1` only. Nothing on the LAN can reach it.

- **Consul.** `ws yard init` and `ws yard join` set `bind-address` to the LAN address. Then Consul listens on that address on ports 8300, 8301 and 8302. The firewall must let the other Workstations reach TCP 8300 and TCP and UDP 8302. See [Create a Yard and join it](../recipes/reach/create-a-yard.md).
- **Traefik and the dashboard.** They stay on `127.0.0.1` until you change `traefik-bind-address` or `dashboard-bind-address`. The address `0.0.0.0` means every address. See [Show your work to a teammate](../recipes/reach/show-your-work.md).
- **Always on `127.0.0.1`.** The admin page of Traefik, and the HTTP API, the DNS and the gRPC of Consul. No setting changes it.

!!! warning "The dashboard has no login"

    A dashboard on a LAN address lets anyone who reaches it change the Services of this Workstation, and its containers through the Docker socket. Add a password before you open it. See [Open the dashboard to your LAN with a password](../recipes/https/dashboard-password.md).
