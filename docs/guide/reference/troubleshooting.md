# Troubleshooting

Find your symptom, run the command, and read the page that fixes it.

## Start here

Two commands name the cause of most problems:

```sh
lvh-yard status
lvh-yard test web
```

`lvh-yard status` checks Consul, Traefik and Docker on this Workstation. `lvh-yard test web` tests the Instances and the Route of one Service.

## Symptoms

| Symptom                                  | Cause                                                                                                                   | Run                                        | Fix                                                                                                                                                             |
| ---------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- | ------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Could not resolve host`                 | DNS does not answer for the Base Domain.                                                                                | `curl -i http://web.lvh.me`                | Allow the Base Domain in your resolver, or use `dev.localhost`. See [Could not resolve host](../start/pick-your-base-domain.md#could-not-resolve-host).         |
| `Failed to connect`                      | Traefik does not run.                                                                                                   | `lvh-yard test web`                        | Run `lvh-yard ws compose start`. See [Failed to connect](../recipes/fix/bad-route.md#failed-to-connect).                                                        |
| `404 page not found`                     | Traefik has no Route for the hostname. The Service is not published, or it has no Instance, or the Base Domain differs. | `lvh-yard catalog`                         | Run `lvh-yard publish web`, or register an Instance. See [404 page not found](../recipes/fix/bad-route.md#404-page-not-found).                                  |
| `502 Bad Gateway`                        | A Route exists, but no Instance answers.                                                                                | `lvh-yard test web`                        | Start the app, or register the Instance at the address that it listens on. See [502 Bad Gateway](../recipes/fix/bad-route.md#502-bad-gateway).                  |
| `unavailable`                            | The Service is published and has no registered Instance. The hostname answers `404`.                                    | `lvh-yard catalog`                         | Register an Instance. See [Your first Service](../start/first-service.md).                                                                                      |
| `unhealthy`                              | No registered Instance passes its check.                                                                                | `lvh-yard test web`                        | Start the Instances, or fix the check. See [Test replicas and failover](../recipes/choose/replicas-and-failover.md).                                            |
| `degraded`                               | One Instance passes, and another does not. The hostname still works.                                                    | `lvh-yard catalog --details`               | Fix the Instance that does not pass. See [Test replicas and failover](../recipes/choose/replicas-and-failover.md).                                              |
| A Remote Service is missing              | Its Home Workstation did not publish it with Yard Scope, or every Instance is out of range.                             | `lvh-yard catalog --scope every`           | On the Home Workstation, run `lvh-yard publish web --scope yard`, and register the Instance at the LAN address. See [Scope](../concepts/scope.md).              |
| An Instance is marked out of range       | It listens on loopback or on a Docker bridge network. Other Workstations cannot reach it.                               | `lvh-yard catalog --details --scope every` | Register the Instance at the LAN address. See [Out-of-range Instances](../concepts/scope.md#out-of-range-instances).                                            |
| `Workstation unreachable`                | The Consul of that Workstation does not answer.                                                                         | `lvh-yard ws compose logs consul`          | Start the Workstation, and allow its ports in the firewall. See [Consul cannot read from ws-b](../recipes/reach/create-a-yard.md#consul-cannot-read-from-ws-b). |
| `LVH Yard on this Workstation: failing`  | Consul, Traefik or Docker fails.                                                                                        | `lvh-yard status`                          | Read the line that starts with `FAIL`, then run `lvh-yard ws compose restart`. See [Install LVH Yard](../start/install.md).                                     |
| `unable to get local issuer certificate` | The client does not trust the Workstation CA.                                                                           | `curl --cacert ca.crt https://web.lvh.me`  | Import `ca.crt` in the client. See [unable to get local issuer certificate](../recipes/https/workstation-ca.md#unable-to-get-local-issuer-certificate).         |
