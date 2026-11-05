package dev.palazon.lvhyard.dashboard.profiles;

import dev.palazon.lvhyard.dashboard.services.OutOfRange;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SequencedSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Checks a set of Route Profiles before any of them reaches Consul KV. A profile must work on every
 * Workstation that routes it (ADR-0004), so it may not read a file, use a plugin, send {@code
 * forwardAuth} to an address only this Workstation reaches, or chain a middleware that is not a
 * Route Profile of this Workstation.
 */
@Component
public class RouteProfileChecks {

  /**
   * Traefik's own HTTP middlewares, less those the spec leaves out: rate and request limits, and
   * path rewrites. Not {@code errors} either: it names a Traefik service, and LVH Yard gives
   * Traefik only its own.
   */
  private static final Set<String> TYPES =
      Set.of(
          "basicAuth",
          "buffering",
          "chain",
          "circuitBreaker",
          "compress",
          "contentType",
          "digestAuth",
          "forwardAuth",
          "grpcWeb",
          "headers",
          "ipAllowList",
          "ipWhiteList",
          "passTLSClientCert",
          "redirectRegex",
          "redirectScheme",
          "retry");

  /** A Consul key part, and Traefik's {@code @} would name another provider. */
  private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]*");

  private final OutOfRange outOfRange;

  RouteProfileChecks(OutOfRange outOfRange) {
    this.outOfRange = outOfRange;
  }

  /** {@code reason} is a full sentence that starts with the profile's name. */
  public record Problem(String profile, String reason) {}

  /**
   * @param problems one for each refused profile, in the order of the set
   * @param definitions each accepted profile's value for Consul KV: the profile and every chain
   *     member it reaches, in Traefik's {@code http.middlewares} shape
   */
  public record Checked(List<Problem> problems, Map<String, Map<String, Object>> definitions) {}

  /**
   * @param profiles each profile's middleware, by name, as YAML or JSON gives it
   * @param stored the profiles in this Workstation's Consul KV. A chain may name them. A profile of
   *     the set replaces the stored one of the same name.
   */
  public Checked check(Map<String, ?> profiles, Map<String, RouteProfileDefinition> stored) {
    Map<String, Object> known = new LinkedHashMap<>();
    stored.values().forEach(definition -> definition.middlewares().forEach(known::putIfAbsent));
    stored.forEach((name, definition) -> {
      if (definition.middlewares().containsKey(name)) {
        known.put(name, definition.middlewares().get(name));
      }
    });
    profiles.forEach((name, middleware) -> known.put(name, withEmptyOptions(middleware)));
    Checker checker = new Checker(known, outOfRange);
    List<Problem> problems = new ArrayList<>();
    Map<String, Map<String, Object>> definitions = new LinkedHashMap<>();
    profiles
        .keySet()
        .forEach(name -> {
          String reason = checker.refusal(name);
          if (reason != null) {
            problems.add(new Problem(name, reason));
            return;
          }
          Map<String, Object> definition = new LinkedHashMap<>();
          for (String member : checker.reached(name)) {
            definition.put(member, known.get(member));
          }
          definitions.put(name, definition);
        });
    return new Checked(problems, definitions);
  }

  /** {@code compress:} with nothing after it is {@code compress: {}} for Traefik. */
  private static Object withEmptyOptions(Object middleware) {
    if (!(middleware instanceof Map<?, ?> map)) {
      return middleware;
    }
    Map<Object, Object> copy = new LinkedHashMap<>();
    map.forEach((type, options) -> copy.put(type, options == null ? Map.of() : options));
    return copy;
  }

  /** The first of the middleware's types that Traefik does not have, else {@code null}. */
  private static String unknownType(Object middleware) {
    if (!(middleware instanceof Map<?, ?> map)) {
      return null;
    }
    return map.keySet().stream()
        .map(String::valueOf)
        .filter(type -> !type.equals("plugin") && !TYPES.contains(type))
        .findFirst()
        .orElse(null);
  }

  /** The type of a middleware with exactly one, else {@code null}. */
  static String type(Object middleware) {
    return middleware instanceof Map<?, ?> map && map.size() == 1
        ? String.valueOf(map.keySet().iterator().next())
        : null;
  }

  private static final class Checker {

    private final Map<String, Object> middlewares;

    private final OutOfRange outOfRange;

    Checker(Map<String, Object> middlewares, OutOfRange outOfRange) {
      this.middlewares = middlewares;
      this.outOfRange = outOfRange;
    }

    /** Why the profile is refused, or {@code null}. */
    String refusal(String name) {
      return refusal(name, new LinkedHashSet<>());
    }

    /** The profile and every chain member it reaches, the profile first. It must be valid. */
    SequencedSet<String> reached(String name) {
      SequencedSet<String> reached = new LinkedHashSet<>();
      reach(name, reached);
      return reached;
    }

    private void reach(String name, SequencedSet<String> reached) {
      if (reached.add(name)) {
        members(name).forEach(member -> reach(member, reached));
      }
    }

    /** {@code chain} is the chain members being checked, so a loop is found. */
    private String refusal(String name, SequencedSet<String> chain) {
      String unknownType = unknownType(middlewares.get(name));
      if (unknownType != null) {
        return name + " has the type " + unknownType + ", which is not a Traefik middleware.";
      }
      if (!NAME.matcher(name).matches()) {
        return name + " is not a valid name. Use letters, digits, - and _.";
      }
      String type = type(middlewares.get(name));
      if (type == null) {
        return name + " must have exactly one middleware type, such as headers or basicAuth.";
      }
      if (type.equals("plugin")) {
        return name + " uses a plugin. Another Workstation's Traefik may not have it.";
      }
      Object options = ((Map<?, ?>) middlewares.get(name)).get(type);
      if (!(options instanceof Map<?, ?> settings)) {
        return name + " must give its " + type + " settings as a map.";
      }
      return switch (type) {
        case "basicAuth", "digestAuth" ->
            settings.containsKey("usersFile")
                ? name
                    + " reads its users from usersFile, a file that other Workstations do not"
                    + " have. Write them under users instead, as hashes from htpasswd."
                : null;
        case "forwardAuth" -> forwardAuthRefusal(name, settings.get("address"));
        case "chain" -> chainRefusal(name, settings.get("middlewares"), chain);
        default -> null;
      };
    }

    private String forwardAuthRefusal(String name, Object address) {
      String host;
      try {
        host = address instanceof String text ? new URI(text).getHost() : null;
      } catch (URISyntaxException e) {
        host = null;
      }
      if (host == null) {
        return name + " must give forwardAuth an address such as http://auth.example:4181/auth.";
      }
      host = host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
      String lower = host.toLowerCase(Locale.ROOT);
      if (lower.equals("localhost") || lower.endsWith(".localhost") || outOfRange.contains(host)) {
        return name
            + " sends forwardAuth to "
            + host
            + ", which only this Workstation reaches. Use an address that every Workstation"
            + " reaches.";
      }
      return null;
    }

    private String chainRefusal(String name, Object members, SequencedSet<String> chain) {
      if (!(members instanceof List<?> list)
          || !list.stream().allMatch(member -> member instanceof String)) {
        return name + " must list the names of its chain's middlewares.";
      }
      chain.add(name);
      try {
        for (Object member : list) {
          String memberName = (String) member;
          if (!middlewares.containsKey(memberName)) {
            return name
                + " names "
                + memberName
                + ", which is not a Route Profile of this"
                + " Workstation.";
          }
          if (chain.contains(memberName)) {
            return name + " is a chain that comes back to " + memberName + ".";
          }
          if (refusal(memberName, chain) != null) {
            return name + " names " + memberName + ", which is refused.";
          }
        }
        return null;
      } finally {
        chain.remove(name);
      }
    }

    private List<String> members(String name) {
      Object middleware = middlewares.get(name);
      if (!"chain".equals(type(middleware))) {
        return List.of();
      }
      Object chain = ((Map<?, ?>) middleware).get("chain");
      return ((List<?>) ((Map<?, ?>) chain).get("middlewares"))
          .stream().map(String.class::cast).toList();
    }
  }
}
