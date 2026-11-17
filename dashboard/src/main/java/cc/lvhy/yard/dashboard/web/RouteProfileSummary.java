package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.profiles.RouteProfileDefinition;
import cc.lvhy.yard.dashboard.services.Service;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Route Profile of a Publication as the Route Diagram shows it: its middlewares in the order the
 * Route applies them, a chain spread into its members, and each option with its value. A value that
 * can hold a secret is left out.
 *
 * @param missing whether the Home Workstation holds no such profile, so Traefik refuses the Route
 */
record RouteProfileSummary(String name, boolean missing, List<Middleware> middlewares) {

  /** A middleware, or the reserved option clientCertificate or mutualTLS. */
  record Middleware(String name, String type, boolean authentication, List<Option> options) {}

  /**
   * @param value {@code null} when it is left out
   */
  record Option(String key, String value) {

    boolean hidden() {
      return value == null;
    }
  }

  /** The options that hold users and their password hashes. */
  private static final Set<String> SECRET_KEYS = Set.of("users", "usersFile");

  static List<RouteProfileSummary> of(Service service) {
    return of(service.profiles(), service.profileDefinitions());
  }

  /**
   * @param definitions the profiles that the Home Workstation holds, by name
   */
  static List<RouteProfileSummary> of(
      List<String> profiles, Map<String, RouteProfileDefinition> definitions) {
    return profiles.stream()
        .map(name -> {
          RouteProfileDefinition definition = definitions.get(name);
          if (definition == null) {
            return new RouteProfileSummary(name, true, List.of());
          }
          List<Middleware> middlewares = new ArrayList<>();
          spread(definition.middlewares(), name, new HashSet<>(), middlewares);
          return new RouteProfileSummary(name, false, List.copyOf(middlewares));
        })
        .toList();
  }

  /** The names a chain lists are spread in order. A name met twice is spread once. */
  private static void spread(
      Map<String, Map<String, Object>> all,
      String name,
      Set<String> seen,
      List<Middleware> middlewares) {
    Map<String, Object> middleware = all.get(name);
    if (middleware == null || middleware.size() != 1 || !seen.add(name)) {
      return;
    }
    Map.Entry<String, Object> only = middleware.entrySet().iterator().next();
    String type = only.getKey();
    Object options = only.getValue();
    if (type.equals("chain")) {
      if (options instanceof Map<?, ?> chain
          && chain.get("middlewares") instanceof List<?> members) {
        members.stream()
            .filter(String.class::isInstance)
            .forEach(member -> spread(all, (String) member, seen, middlewares));
      }
      return;
    }
    List<Option> shown = new ArrayList<>();
    if (type.equals("plugin") && options instanceof Map<?, ?> plugins) {
      // Plugin options are free-form, so no one can tell which of them is secret.
      plugins.keySet().forEach(plugin -> shown.add(new Option(String.valueOf(plugin), null)));
    } else {
      collect("", type, options, shown);
    }
    middlewares.add(
        new Middleware(
            name, type, RouteProfileDefinition.asksWhoTheUserIs(type), List.copyOf(shown)));
  }

  private static void collect(String key, String type, Object value, List<Option> options) {
    switch (value) {
      case Map<?, ?> map -> {
        Map<String, Object> ordered = new LinkedHashMap<>();
        map.forEach((k, v) -> ordered.put(String.valueOf(k), v));
        ordered.forEach((k, v) -> {
          String path = key.isEmpty() ? k : key + "." + k;
          if (SECRET_KEYS.contains(k)) {
            options.add(new Option(path, null));
          } else {
            collect(path, type, v, options);
          }
        });
      }
      case List<?> list -> {
        if (list.stream().allMatch(item -> !(item instanceof Map || item instanceof List))) {
          options.add(
              new Option(key, String.join(", ", list.stream().map(String::valueOf).toList())));
        } else {
          for (int i = 0; i < list.size(); i++) {
            collect(key + "[" + i + "]", type, list.get(i), options);
          }
        }
      }
      case null -> {}
      default -> {
        if (type.equals("forwardAuth") && key.equals("address")) {
          options.add(new Option(key, withoutCredentials(String.valueOf(value))));
        } else {
          options.add(new Option(key, String.valueOf(value)));
        }
      }
    }
  }

  /** The address with no user, password or query, or {@code null} when it is not an address. */
  static String withoutCredentials(String address) {
    try {
      URI uri = new URI(address);
      if (uri.getHost() == null) {
        return null;
      }
      return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null)
          .toString();
    } catch (URISyntaxException e) {
      return null;
    }
  }
}
