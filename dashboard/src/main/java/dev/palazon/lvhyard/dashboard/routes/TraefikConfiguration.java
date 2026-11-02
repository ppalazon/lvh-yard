package dev.palazon.lvhyard.dashboard.routes;

import java.util.List;
import java.util.Map;

/**
 * Traefik's dynamic configuration, as its HTTP provider reads it (ADR-0002). Only the parts LVH
 * Yard writes.
 */
record TraefikConfiguration(Http http) {

  record Http(Map<String, Router> routers, Map<String, Service> services) {}

  record Router(String rule, List<String> entryPoints, String service) {}

  record Service(LoadBalancer loadBalancer) {}

  record LoadBalancer(List<Server> servers) {}

  record Server(String url) {}
}
