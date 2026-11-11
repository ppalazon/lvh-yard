# LVH Yard

LVH Yard makes development services on every Workstation in a Yard reachable through predictable hostnames, without remembering their IP addresses or ports. It manages services, not the processes behind them.

## Language

### Things

**Yard**:
All the Workstations on one LAN that can see each other's Services.
_Avoid_: Cluster, network, mesh

**Workstation**:
A computer in the Yard that hosts Instances and opens hostnames for Services, unless its Traefik is off. It is yours, a coworker's, or the team's.
_Avoid_: Machine, host, node, server, client

**Service**:
A named, logical thing you want to reach, such as `grafana`. It has no address of its own. You create it with a name, and it stays, even with no Instances, until you delete it. A Service that only Foreign Instances give exists while they are registered.
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
A link from a Service to a Docker container, by container name or by Compose project and service, and a container port. Every running container that matches gives the Service a Bound Instance. Any number of Services can have a Container Binding to the same container or Compose service.
_Avoid_: Docker registration, container mapping

**Bound Instance**:
An Instance created and removed by a Container Binding, at the matching container's current address and port.
_Avoid_: Dynamic instance, container instance

**Foreign Instance**:
An Instance that another tool registered straight in Consul, not LVH Yard. LVH Yard shows and routes it as an Instance Source of its own, but cannot change or remove it, and a Backup does not hold it.
_Avoid_: External instance, unmanaged instance

**Instance Source**:
One thing that gives a Service Instances: a Static Instance, a Container Binding, an Alias or a Foreign Instance. It can give none, one or many Instances.
_Avoid_: Backend, attachment, upstream

**Container Port**:
The port a process listens on inside a container. A Container Binding names one.
_Avoid_: Internal port, exposed port

**Host Port**:
The port on a Workstation where a Container Port is open. It is the port that Docker maps to the Container Port. For a container on the host network, it is the Container Port itself. When a Container Port has a Host Port, its Bound Instance is at the Workstation's address and the Host Port.
_Avoid_: Exposed port, public port, mapped port

**Unfollowed Container**:
A running container on this Workstation that no Container Binding matches, whatever the Binding's port. `lvh-yard containers` lists them.
_Avoid_: Unbound container, new container, orphan container

### Setting up a Workstation

**Workstation Folder**:
The folder that holds one Workstation's setup files. There is one per Workstation.
_Avoid_: Config folder, config dir, install dir

**Backup**:
A YAML file that holds what you set up on one Workstation: its Services with their Publications, Instance Sources and Instance Priority, and its Route Profiles. You export a Backup from one Workstation and import it into the same Workstation or another one. It never holds Bound Instances, Health or Remote Services, because those come from what runs now.
_Avoid_: Snapshot, dump, export file, setup file

**Peer**:
The LAN address of another Workstation that this Workstation joins to find the Yard. One is enough, and the first Workstation of a Yard has none.
_Avoid_: Seed, neighbour, node

**WAN pool**:
The Workstations whose Consul datacenters are federated with this Workstation's, as its Consul sees them now. Each one is alive or not. A Peer with no member at its address there is a join that failed.
_Avoid_: Cluster, federation list, Yard (the Yard is every Workstation, but the WAN pool is what Consul sees)

**Gossip Key**:
The Yard's shared secret. Every Workstation in the Yard needs the same one to join it.
_Avoid_: Yard key, encryption key, secret

**Yard CA**:
The one certificate authority of a Yard. It signs every Workstation Certificate and Client Certificate. LVH Yard can make it, or you bring your own. Its key stays on the Workstation that made it.
_Avoid_: Private CA, root certificate

**Workstation Certificate**:
The certificate and key that the Yard CA signs for one Workstation. Consul uses it between Workstations, and Traefik uses it for HTTPS on that Workstation's Routes.
_Avoid_: Server certificate, TLS cert

**Client Certificate**:
A certificate that the Yard CA signs for a person. A Route Profile with `clientCertificate: {}` requires it. That Route answers only on HTTPS and has no Route on a Workstation without HTTPS.
_Avoid_: User certificate, mTLS certificate

**Base Domain**:
The domain that every hostname on one Workstation ends with, such as `lvh.me`. Each Workstation picks its own, and it can be a public domain.
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
One piece of LVH Yard with a Status of its own: Consul, Traefik or Docker. A Workstation with Traefik off has no Traefik Status Part.
_Avoid_: Component, subsystem, check

**Reachability**:
Whether the Workstation you are using can actually connect to an Instance, directly and through its Route. It is tested on demand and never stored.
_Avoid_: Connectivity, health, availability

**Service Warning**:
A problem that the Home Workstation's setup of a Service causes, found from that setup alone. It has one of two levels. If the hostname does not work, the level is **error**. If the hostname works but other Workstations or users can meet a problem, the level is **risk**. The Service's state is not a Service Warning.
_Avoid_: Alert, issue, Health warning, Status warning

**Publication**:
The Home Workstation's opt-in choice to make a Service reachable under a hostname, with a Scope. It also says whether Traefik logs the requests to its Route. It belongs to the Service, not to its Instances, and never starts or stops a process.
_Avoid_: Exposure, routing enabled, enabled

**Scope**:
Who gets a Route for a Publication: **Local** (only the Home Workstation) or **Yard** (every Workstation). It decides who gets a hostname, not who can connect.
_Avoid_: Visibility, access, permission

**Alias**:
A link from a Service to a Remote Service, whose Instances the Service can use. It has no hostname of its own. A Service can have many Aliases, and only the Home Workstation's Route uses them.
_Avoid_: Rename, nickname, CNAME, short hostname

**Instance Priority**:
The order of a Service's Instance Sources. Its Route uses only the first Instance Source that has an Instance with passing Health. When it is off, every passing Instance of every Instance Source shares the Route.
_Avoid_: Alias priority, weight, preference, kind order

**Route Profile**:
A named, reusable set of Route options, such as authentication, added headers or a required Client Certificate, that a Publication can use. It is written on the Home Workstation, and every Workstation that routes the Publication applies it. A Route that uses the Instances of an Alias applies the Route Profiles of its own Service, not those of the Remote Service.
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
A Service with no Publication, by your choice. Its Instances can be registered and healthy.
Not the same as an unpublished container port, which is Docker's word for a container port not mapped to a host port (`docker run -p`). A Bound Instance on one is at its container's own address, so a Yard-scoped Service warns about it.

**Unavailable**:
A published Service with no registered Instances.
_Avoid_: Down, offline

**Unhealthy**:
A published Service whose registered Instances have no passing Health.
_Avoid_: Down, failing, broken

**Degraded**:
A published Service with at least one passing Instance and at least one Instance that does not pass. Its hostname still works, because the Route uses only passing Instances. It is a Service state, not a Status.
_Avoid_: Partial, partly down, degraded Status

**Healthy**:
A published Service whose registered Instances all pass.
_Avoid_: Up, OK, online
