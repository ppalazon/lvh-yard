# Study: keep Route Profile secrets in Vault

Status: needs-triage

Route Profiles travel through Consul KV (ADR-0004), so basic-auth hashes and forward-auth tokens are readable by every Workstation in the Yard. Study whether HashiCorp Vault could hold these secrets instead, with profiles naming them.

Questions to answer:

- One Vault per Workstation, like Consul (ADR-0001), or one for the Yard?
- How a Workstation reads another Workstation's secret, and who may.
- How Traefik gets the secret: through the dashboard's HTTP provider, or straight from Vault.
- What `lvh-yard ws init` would add to the Workstation Folder's `compose.yaml` and `.env`.
- What it costs a Yard that does not want Vault.
