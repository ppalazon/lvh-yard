---
title: "A Publication is a key in this Workstation's Consul KV, and the Yard reader gives each Service its hostname"
author: Pablo
date: 2026-10-30
branch: main
cost: { minutes: 1, tokens: 1785 }
id: l-202f8cea
scope: repo
status: active
src: agent
---

## Decided
- A Publication is the key lvh-yard/publications/<service> in the Home Workstation's Consul KV. Its value is the Scope as plain text ("local"). Publishing never touches an Instance.
- The API publishes with PUT and unpublishes with DELETE on /api/workstation/services/{service}/publication. PUT answers with the whole Service, so `yard publish` can print the hostname without knowing the base domain.
- Publishing needs the Service to exist (an Instance or a Publication), else 404. This stops a typo from creating an empty Service. Publishing `yard` is refused with 400.
- Each Service in the API has a `state`: unpublished, unavailable, unhealthy or healthy. The last one is `healthy`, not `published`: an Unhealthy or Unavailable Service is published too, and Service.published() means "has a Publication".
- The hostname rule lives in one place: YardReader gives each published Service its `hostname`, and RouteBuilder routes to that hostname. When the API and Traefik each built the hostname, they could drift apart once Remote Services and Aliases arrive. This bends the spec's line "the Route builder holds all the hostname rules".
- A published Service with no Instances (Unavailable) gets no Route, but it keeps its hostname in the API and pages, so you still see where it will answer.
- The `yard` Route comes from the `yard` Core Service. RouteBuilder adds a fixed one to the dashboard only while that Service has no Route, so the dashboard is always reached and no other Service can take the name (it is reserved).

## Watch out
- Publications.checkExists counts only Instances and Publications. The spec says a Service also exists while it has a Container Binding, so the check must count Bindings once they exist.
- The API has one `hostname` string. Aliases may need a list of hostnames, which would change the contract.

In the code: dashboard/.../services/Publications.java, YardReader.java, ServiceState.java; dashboard/.../routes/RouteBuilder.java; cli/.../Publish.java.
