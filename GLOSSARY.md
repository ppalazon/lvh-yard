# LVH Yard

LVH Yard makes development services on every Workstation in a Yard reachable through predictable hostnames, without remembering their IP addresses or ports. It manages services, not the processes behind them.

## Language

### Things

**Yard**:
All the Workstations on one LAN that can see each other's Services.
_Avoid_: Cluster, network, mesh

**Workstation**:
A computer in the Yard that hosts Instances and opens hostnames for Services. It may be yours, a coworker's, or shared by the team.
_Avoid_: Machine, host, node, server, client

**Service**:
A named, logical thing you want to reach, such as `grafana`. It has no address of its own.
_Avoid_: App, application, endpoint

**Home Workstation**:
The Workstation a Service and all its Instances run on. Only the Home Workstation can change the Service.
_Avoid_: Owner, origin

**Remote Service**:
A Service seen from any Workstation other than its Home Workstation.
_Avoid_: Foreign service, external service

**Instance**:
One running copy of a Service, at a host and port, on one Workstation. A Service can have many Instances.
_Avoid_: Replica, backend, container, process

**Static Instance**:
An Instance at a fixed host and port that you give. It stays until you remove it.
_Avoid_: Manual instance, fixed instance

**Container Binding**:
A link from a Service to a Docker container, by container name or by Compose project and service, and a container port. Every running container that matches gives the Service a Bound Instance.
_Avoid_: Docker registration, container mapping

**Bound Instance**:
An Instance created and removed by a Container Binding, at the matching container's current address and port.
_Avoid_: Dynamic instance, container instance

**Core Service**:
A Service that LVH Yard itself keeps registered and published on every Workstation: the dashboard (`yard`), Consul and Traefik. You cannot remove or unpublish it.
_Avoid_: System service, built-in service

### Setting up a Workstation

**Workstation Folder**:
The folder that holds one Workstation's setup files. There is one per Workstation.
_Avoid_: Config folder, config dir, install dir

**Peer**:
The LAN address of another Workstation that this Workstation joins to find the Yard. One is enough, and the first Workstation of a Yard has none.
_Avoid_: Seed, neighbour, node

**WAN pool**:
The Workstations whose Consul datacenters are federated with this Workstation's, as its Consul sees them now. Each one is alive or not. A Peer with no member at its address there is a join that failed.
_Avoid_: Cluster, federation list, Yard (the Yard is every Workstation; the WAN pool is what Consul sees)

**Gossip Key**:
The Yard's shared secret. Every Workstation in the Yard needs the same one to join it.
_Avoid_: Yard key, encryption key, secret

**Base Domain**:
The domain that every hostname on one Workstation ends with, such as `lvh.me`. Each Workstation picks its own, and it may be a public domain.
_Avoid_: Yard domain, TLD, suffix, zone

### Facts about them

**Registration**:
An Instance being known to the service registry. It says nothing about whether the Instance works or is routed.
_Avoid_: Discovery, enrolment

**Health**:
The result of an Instance's checks: passing, warning or critical.
_Avoid_: Status, availability, liveness

**Status**:
How LVH Yard itself works on one Workstation: each Status Part, and the worst of them. Each is OK, warning or failing, with a reason. It is about LVH Yard's own pieces, never about a Service.
_Avoid_: Health, state, availability

**Status Part**:
One piece of LVH Yard with a Status of its own: Consul, Traefik, Docker or the Core Services.
_Avoid_: Component, subsystem, check

**Reachability**:
Whether the Workstation you are using can actually connect to an Instance, directly and through its Route. It is tested on demand and never stored.
_Avoid_: Connectivity, health, availability

**Publication**:
The Home Workstation's opt-in choice to make a Service reachable under a hostname, with a Scope. It belongs to the Service, not to its Instances, and never starts or stops a process.
_Avoid_: Exposure, routing enabled, enabled

**Scope**:
Who gets a Route for a Publication: **Local** (only the Home Workstation) or **Yard** (every Workstation). It decides who gets a hostname, not who can connect.
_Avoid_: Visibility, access, permission

**Alias**:
A short hostname one Workstation gives to a Remote Service, for its own use only. When a Service of that Workstation has the same name, the Alias becomes that Service's last fallback under its Instance Priority.
_Avoid_: Rename, nickname, CNAME

**Instance Priority**:
The order of Instance kinds a Route prefers: by default Static Instances, then Bound Instances, then the Remote Service of an Alias with the same name. The Route uses only the first kind that has a passing Health. An empty order turns it off: the passing Static and Bound Instances then share the Route, and an Alias is never used.
_Avoid_: Alias priority, weight, preference

**Route Profile**:
A named, reusable set of Route options, such as authentication or added headers, that a Publication can use. It is written on the Home Workstation, and every Workstation that routes the Publication applies it, Aliases included.
_Avoid_: Middleware, Traefik profile, policy

**Route**:
The proxy rule built from a Publication, which sends a hostname to the Service's Instances.
_Avoid_: Router, mapping

**Unreachable Workstation**:
A Workstation whose Consul datacenter does not answer. Its Services are not shown until it answers again.
_Avoid_: Offline, down, unreachable Service

**Out-of-range Instance**:
An Instance of a Remote Service at an address only its Home Workstation can connect to: loopback, or a Docker bridge network (172.17.0.0/16 to 172.31.0.0/16 unless `YARD_OUT_OF_RANGE` says otherwise). Other Workstations mark it and never route to it. It is known from the address alone, not from a Reachability test.
_Avoid_: Unreachable Instance, loopback Service, unreachable Service

### Service states

**Unpublished**:
A Service with no Publication, by your choice. Its Instances may be registered and healthy.
Not the same as an unpublished container port, which is Docker's word for a container port not mapped to a host port (`docker run -p`). A Bound Instance on one is at its container's own address, so a Yard-scoped Service warns about it.

**Unavailable**:
A published Service with no registered Instances.
_Avoid_: Down, offline

**Unhealthy**:
A published Service whose registered Instances have no passing Health.
_Avoid_: Down, failing, broken
