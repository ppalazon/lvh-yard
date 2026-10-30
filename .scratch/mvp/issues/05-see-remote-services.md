# 05: See Remote Services across the Yard

**What to build:** A developer sees every Service in the Yard, grouped by Home Workstation, in the Catalog, the API and `yard list`. A Remote Service can be looked at but not changed. A Workstation that does not answer shows as "Workstation unreachable". The dashboard tests gain a Yard of two federated Consul datacenters.

**Blocked by:** 01 (Register a Static Instance and see it in the Catalog), 02 (CLI: `yard list`, `yard register`, `yard remove`)

**Status:** done

- [x] Dashboard tests can start two Consul datacenters joined by WAN federation.
- [x] The Catalog, the API and `yard list` group Services by Home Workstation, this Workstation first.
- [x] A Remote Service is addressed by Workstation and Service name in the API and the CLI.
- [x] A Remote Service's detail page shows its Instances and Health, and has no forms or controls.
- [x] The API refuses to register, edit, remove, publish or unpublish a Remote Service, with a client error that explains why.
- [x] A Workstation listed as a peer whose datacenter does not answer shows as "Workstation unreachable", and the rest of the Catalog still loads.
- [x] A Workstation's Services show again by themselves when its datacenter answers again.
