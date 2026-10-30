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

### Facts about them

**Registration**:
An Instance being known to the service registry. It says nothing about whether the Instance works or is routed.
_Avoid_: Discovery, enrolment

**Health**:
The result of an Instance's checks: passing, warning or critical.
_Avoid_: Status, availability, liveness

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
A short hostname one Workstation gives to a Remote Service, for its own use only. A Service of that Workstation with the same name wins over it.
_Avoid_: Rename, nickname, CNAME

**Route**:
The proxy rule built from a Publication, which sends a hostname to the Service's Instances.
_Avoid_: Router, mapping

**Unreachable Workstation**:
A Workstation whose Consul datacenter does not answer. Its Services are not shown until it answers again.
_Avoid_: Offline, down, unreachable Service

### Service states

**Unpublished**:
A Service with no Publication, by your choice. Its Instances may be registered and healthy.

**Unavailable**:
A published Service with no registered Instances.
_Avoid_: Down, offline

**Unhealthy**:
A published Service whose registered Instances have no passing Health.
_Avoid_: Down, failing, broken
