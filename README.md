# LVH Yard

LVH Yard is a local centric development tool to enabled access to any of your
http services using DNS domains. When you are developing or using local http
services, the usual way to access is through `localhost:port` combination. The
usual ports are `8080`, `3000`, `8000`, etc, but if you have so many of them or
even in different hosts, these combinations are getting pretty complex to
remember.

With this tool you would be able to create a environment configuration for all
these services using any domain in a very simple way. The trick is using
wildcard domain that points directly to `localhost`. There are some domain like
`*.lvh.me` that points towards `127.0.0.1`. If you don't trust this domains,
you can create your own wildcard subdomain.

This application is supported by
[HashiCorp Consul](https://www.hashicorp.com/en/products/consul) and
[Traefik](https://doc.traefik.io/traefik/). Consul provides with service
discovery and a standard service networking, and Traefik is a reverse-proxy
service in front of your http services.

What could you do with this application?:

- Have a DNS domain for each service that you have deployed into:
  - Your workstation.
  - Any workstation in your LAN or HomeLab.
- Bind docker container or compose services to a single name service without
  worry the port number where they're exposed.
- Test your service behind a reverse-proxy.
- Configure the reverse-proxy with TLS, mTLS, CORS, Authentication, Header
  Validation, etc
  ([Traefik Proxy Middleware Overview](https://doc.traefik.io/traefik/reference/routing-configuration/http/middlewares/overview/)).
- Validate your microservices architecture with multiples replicas in a
  production-like environment.

## Example Scenario

Just imagine that you're working in a microservices architecture with a
FrontEnd application based on JavaScript (port `3000`), a Backend application
based on Spring Boot (port `8080`), your project documents on MkDocs (port
`8000`), and any other docker compose service that you need such as Mailpit
(port `8025`). This situation could be very easy to remember, but this
configuration could be repeat again in just another project. So, you can use
any other fixed port for these services and use DNS domains to access to them.

With LVH Yard you could create these service:

- `front.lvh.me` -> `localhost:3000`
- `back.lvh.me` -> `localhost:8080`
- `mkdocs.lvh.me` -> `localhost:8000`
- `mailpit.lvh.me` -> `localhost:34556` (Docker Service)

All the request will go to `localhost:80` where Traefik listens, and it
forwards the http request to the declared services by `Host`.

- [User guide](docs/user-guide.md): install and use LVH Yard.
- [Development](docs/development.md): build, test and release LVH Yard.

## AI-assisted development

This project was developed with assistance from AI coding tools, including
Anthropic Claude.

The project maintainer is responsible for its architecture, implementation
decisions, code review, testing, and maintenance.

AI-generated contributions are reviewed before inclusion in the project.

## License

LVH Yard is licensed under the Apache License, Version 2.0. See the
[LICENSE](LICENSE) file for details.
