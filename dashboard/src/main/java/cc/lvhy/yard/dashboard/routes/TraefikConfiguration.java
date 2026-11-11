package cc.lvhy.yard.dashboard.routes;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Traefik's dynamic configuration, as its HTTP provider reads it (ADR-0002). Only the parts LVH
 * Yard writes.
 */
record TraefikConfiguration(Http http) {

  /**
   * @param middlewares the Route Profiles' middlewares, by their name in Traefik, each in Traefik's
   *     own shape
   */
  record Http(
      Map<String, Router> routers,
      Map<String, Service> services,
      @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Map<String, Object>> middlewares) {}

  record Router(
      String rule,
      List<String> entryPoints,
      String service,
      @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> middlewares,
      @JsonInclude(JsonInclude.Include.NON_NULL) Observability observability) {}

  /** Traefik writes the access log for a router unless this says false. */
  record Observability(boolean accessLogs) {}

  record Service(LoadBalancer loadBalancer) {}

  record LoadBalancer(List<Server> servers) {}

  record Server(String url) {}
}
