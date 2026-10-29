# 09: Bind a Service to a Compose service

**What to build:** A developer binds a Service to a Docker Compose project and service, whatever its containers are named. A scaled Compose service gives one Bound Instance per container.

**Blocked by:** 08 (Bind a Service to a container by name)

**Status:** ready-for-agent

- [ ] A Container Binding can name a Compose project and service, plus a container port, instead of a container name.
- [ ] Every running container of that project and service gives a Bound Instance.
- [ ] Scaling the Compose service up or down adds or removes Bound Instances to match.
- [ ] The Register form and `yard register` offer both ways to bind.
