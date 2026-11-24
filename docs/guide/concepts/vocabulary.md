# Vocabulary

Ten words carry the guide. Each word means the same on every page. [GLOSSARY.md](https://github.com/ppalazon/lvh-yard/blob/main/GLOSSARY.md) has the full list.

| Word           | It means                                                                                      | Read                                                 |
| -------------- | --------------------------------------------------------------------------------------------- | ---------------------------------------------------- |
| Yard           | All the Workstations on one LAN that can see each other's Services.                           | [The Yard](the-yard.md)                              |
| Workstation    | A computer that runs LVH Yard, hosts Instances and opens hostnames for Services.              | [Install](../start/install.md)                       |
| Service        | A name for something that you want to reach, such as `grafana`. It stays until you delete it. | [First Service](../start/first-service.md)           |
| Instance       | One running copy of a Service, at a host and a port.                                          | [Instance Sources](instance-sources-and-priority.md) |
| Publication    | The choice to give a Service a hostname, with a Scope.                                        | [First Service](../start/first-service.md)           |
| Scope          | Who gets a hostname: Local, only the Home Workstation, or Yard, every Workstation.            | [Scope](scope.md)                                    |
| Base Domain    | The domain that every hostname of a Workstation ends with, such as `lvh.me`.                  | [Base Domain](../start/pick-your-base-domain.md)     |
| Route          | The rule that sends a hostname to the Instances of a Service.                                 | [How it works](how-it-works.md)                      |
| Route Profile  | A named set of options, such as basic auth, that a Publication adds to its Route.             | [Route Profiles](../recipes/https/route-profiles.md) |
| Workstation CA | The certificate authority of one Workstation. It signs the certificates of that Workstation.  | [Workstation CA](../recipes/https/workstation-ca.md) |

A Base Domain is a shared domain when someone else owns it, such as `lvh.me`. It is an own domain when you own it.
