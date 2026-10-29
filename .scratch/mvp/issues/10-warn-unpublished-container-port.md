# 10: Warn when teammates cannot reach a Bound Instance

**What to build:** A Yard-scoped Service warns when one of its Bound Instances uses a container port that is not published on the host, because no other Workstation can reach it.

**Blocked by:** 06 (Share a Service with Yard Scope), 08 (Bind a Service to a container by name)

**Status:** ready-for-agent

- [ ] A Yard-scoped Service with a Bound Instance on an unpublished container port shows a warning on its detail page and in the API.
- [ ] The warning goes away when the container is restarted with the port published, or the Scope is set back to Local.
- [ ] A Local-scoped Service shows no such warning.
