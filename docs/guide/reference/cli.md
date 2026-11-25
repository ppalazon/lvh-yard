# CLI cheat sheet

One line for each command, grouped by task. `lvh-yard <command> --help` lists every option. These flags work on many commands:

- `--dashboard <url>` uses the dashboard at that address.
- `--dir <folder>` points a `ws` command at another Workstation Folder.

## Look

| Command                               | Result                                                                              |
| ------------------------------------- | ----------------------------------------------------------------------------------- |
| `lvh-yard status`                     | Checks that LVH Yard works on this Workstation. `--strict` also fails on a warning. |
| `lvh-yard catalog`                    | Lists the Services of every Workstation, one line each.                             |
| `lvh-yard catalog --details`          | Adds the address, Health and ID of each Instance, and the Service Warnings.         |
| `lvh-yard catalog --workstation ws-b` | Lists only the Services of one Workstation.                                         |
| `lvh-yard catalog --state degraded`   | Lists only the Services in one state.                                               |
| `lvh-yard catalog --scope every`      | Adds the Remote Services that have no Route here. Also `local` and `yard`.          |
| `lvh-yard containers`                 | Lists the running containers that no Service follows.                               |
| `lvh-yard containers --details`       | Adds the container names and the port mappings.                                     |
| `lvh-yard ws show`                    | Prints the settings. It hides the Gossip Key.                                       |

Full reference: [the CLI section of the user guide](https://github.com/ppalazon/lvh-yard/blob/main/docs/user-guide.md#using-the-cli).

## Create

| Command                       | Result                                      |
| ----------------------------- | ------------------------------------------- |
| `lvh-yard service create web` | Creates an empty, Unpublished Service.      |
| `lvh-yard service delete web` | Deletes a Service and its Instance Sources. |

Full reference: [Services and Publications](https://github.com/ppalazon/lvh-yard/blob/main/docs/user-guide.md#services-and-publications).

## Bind

| Command                                                                              | Result                                                               |
| ------------------------------------------------------------------------------------ | -------------------------------------------------------------------- |
| `lvh-yard register static web --port 8080`                                           | Registers a Static Instance at the address of this Workstation.      |
| `lvh-yard register static web --host 127.0.0.1 --port 8080`                          | Registers a Static Instance at another address.                      |
| `lvh-yard register static web --port 8080 --path /health --interval 5s --timeout 1s` | Adds an HTTP check. The default is a TCP check.                      |
| `lvh-yard register container shop-web-1`                                             | Binds one container to a Service.                                    |
| `lvh-yard register compose shop/web --port 80`                                       | Binds a Compose service to a Service.                                |
| `lvh-yard alias add web --workstation ws-b --service web`                            | Adds an Alias to the Remote Service `web` of `ws-b`.                 |
| `lvh-yard alias list web`                                                            | Lists the Aliases of a Service.                                      |
| `lvh-yard alias remove web --workstation ws-b --service web`                         | Removes one Alias.                                                   |
| `lvh-yard remove web --port 8080`                                                    | Removes a Static Instance. Add `--host` when several share the port. |
| `lvh-yard remove web --id <id>`                                                      | Removes an Instance by the ID that `catalog --details` shows.        |
| `lvh-yard remove web --container web-1`                                              | Removes a Container Binding.                                         |
| `lvh-yard remove web --compose-project shop --compose-service web`                   | Removes a Compose binding.                                           |

Full reference: [Static Instances](https://github.com/ppalazon/lvh-yard/blob/main/docs/user-guide.md#static-instances), [Container Bindings](https://github.com/ppalazon/lvh-yard/blob/main/docs/user-guide.md#container-bindings) and [Aliases](https://github.com/ppalazon/lvh-yard/blob/main/docs/user-guide.md#aliases).

## Publish

| Command                                | Result                                                              |
| -------------------------------------- | ------------------------------------------------------------------- |
| `lvh-yard publish web`                 | Gives the Service a hostname on this Workstation, with Local Scope. |
| `lvh-yard publish web --scope yard`    | Gives it a hostname on every Workstation, with Yard Scope.          |
| `lvh-yard publish web --access-log on` | Makes Traefik log the requests to its Route.                        |
| `lvh-yard publish web --mutual-tls on` | Makes Traefik call the Instances over mutual TLS.                   |
| `lvh-yard unpublish web`               | Removes the Publication. The Instances stay.                        |

Full reference: [Services and Publications](https://github.com/ppalazon/lvh-yard/blob/main/docs/user-guide.md#services-and-publications).

## Order

| Command                           | Result                                                        |
| --------------------------------- | ------------------------------------------------------------- |
| `lvh-yard priority web`           | Shows the Instance Sources in order, with their IDs.          |
| `lvh-yard priority web <id> <id>` | Puts these sources first.                                     |
| `lvh-yard priority web --off`     | Makes the Route use every passing Instance. The order stays.  |
| `lvh-yard priority web --on`      | Makes the Route use the first source with a passing Instance. |

Full reference: [Instance Priority](https://github.com/ppalazon/lvh-yard/blob/main/docs/user-guide.md#instance-priority).

## Protect

| Command                                     | Result                                                                |
| ------------------------------------------- | --------------------------------------------------------------------- |
| `lvh-yard profiles`                         | Lists the Route Profiles and the Services that use them.              |
| `lvh-yard profiles show basic-auth`         | Prints one Route Profile in Traefik's format.                         |
| `lvh-yard profiles set profiles.yaml`       | Saves the Route Profiles of a file. `-` reads standard input.         |
| `lvh-yard profiles edit basic-auth`         | Edits one Route Profile in your editor.                               |
| `lvh-yard profiles remove basic-auth`       | Removes a Route Profile.                                              |
| `lvh-yard publish web --profile basic-auth` | Applies a Route Profile to a Publication. Repeat it to set the order. |
| `lvh-yard publish web --no-profiles`        | Removes all Route Profiles from a Publication.                        |

Full reference: [Route Profiles](https://github.com/ppalazon/lvh-yard/blob/main/docs/user-guide.md#route-profiles).

## Test

| Command                                    | Result                                            |
| ------------------------------------------ | ------------------------------------------------- |
| `lvh-yard test web`                        | Tests whether this Workstation reaches a Service. |
| `lvh-yard test grafana --workstation ws-b` | Tests a Remote Service of `ws-b`.                 |

Full reference: [Reachability tests](https://github.com/ppalazon/lvh-yard/blob/main/docs/user-guide.md#reachability-tests).

## Move

| Command                               | Result                                                                |
| ------------------------------------- | --------------------------------------------------------------------- |
| `lvh-yard export -o ws-a.yaml`        | Writes a Backup of this Workstation to a file that only you can read. |
| `lvh-yard import ws-a.yaml`           | Adds the new items of a Backup and skips what exists.                 |
| `lvh-yard import --update ws-a.yaml`  | Also overwrites what exists with the Backup.                          |
| `lvh-yard import --dry-run ws-a.yaml` | Shows the plan and changes nothing.                                   |

Full reference: [Backups](https://github.com/ppalazon/lvh-yard/blob/main/docs/user-guide.md#backups).

## Run the Workstation

| Command                           | Result                                                                   |
| --------------------------------- | ------------------------------------------------------------------------ |
| `lvh-yard ws init`                | Writes the setup files of this Workstation.                              |
| `lvh-yard ws set <name> <value>`  | Changes one setting.                                                     |
| `lvh-yard ws compose start`       | Starts the Compose services.                                             |
| `lvh-yard ws compose stop`        | Stops them.                                                              |
| `lvh-yard ws compose restart`     | Restarts them.                                                           |
| `lvh-yard ws compose recreate`    | Recreates them to apply new settings.                                    |
| `lvh-yard ws compose logs consul` | Shows the logs of `consul`, `traefik` or `dashboard`. `-f` follows them. |
| `lvh-yard ws compose update`      | Updates the images in `compose.yaml`.                                    |
| `lvh-yard ws compose down`        | Removes the containers. `-v` removes the data volumes too.               |
| `lvh-yard update`                 | Installs a release of lvh-yard. `--check` only shows the release.        |

Full reference: [the Workstation section of the user guide](https://github.com/ppalazon/lvh-yard/blob/main/docs/user-guide.md#running-on-a-workstation).

## Join a Yard

| Command                               | Result                                                                 |
| ------------------------------------- | ---------------------------------------------------------------------- |
| `lvh-yard ws yard init`               | Opens this Workstation to a Yard.                                      |
| `lvh-yard ws yard share ws-b`         | Writes the Invitation for `ws-b`.                                      |
| `lvh-yard ws yard join ws-b-yard.zip` | Joins a Yard with an Invitation.                                       |
| `lvh-yard ws yard peers`              | Lists the Peers that Consul sees. `--sync` saves the ones that answer. |
| `lvh-yard ws yard standalone`         | Leaves the Yard.                                                       |

Full reference: [the Yard section of the user guide](https://github.com/ppalazon/lvh-yard/blob/main/docs/user-guide.md#joining-a-yard).

## Certificates

| Command                                                 | Result                                                          |
| ------------------------------------------------------- | --------------------------------------------------------------- |
| `lvh-yard ws tls ca create`                             | Makes the Workstation CA.                                       |
| `lvh-yard ws tls https create`                          | Signs the HTTPS certificate of Traefik and enables HTTPS.       |
| `lvh-yard ws tls https import --cert c.pem --key k.pem` | Imports an HTTPS certificate that you own.                      |
| `lvh-yard ws tls https enable`                          | Enables HTTPS. `disable` disables it.                           |
| `lvh-yard ws tls ca issue service web`                  | Signs a certificate for a Service.                              |
| `lvh-yard ws tls ca issue user ana`                     | Signs a Client Certificate for a person.                        |
| `lvh-yard ws tls client create`                         | Makes Traefik trust the Workstation CA for Client Certificates. |
| `lvh-yard ws tls client import --ca ca.pem`             | Makes Traefik trust another CA for Client Certificates.         |
| `lvh-yard ws tls mtls create`                           | Signs the Traefik certificate for mutual TLS to Services.       |
| `lvh-yard ws tls mtls import`                           | Imports the Traefik certificate and the CA for mutual TLS.      |

`client` and `mtls` also take `enable` and `disable`. Full reference: [Workstation CA and certificates](https://github.com/ppalazon/lvh-yard/blob/main/docs/user-guide.md#workstation-ca-and-certificates).
