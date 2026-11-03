package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.consul.Consul;
import dev.palazon.lvhyard.dashboard.consul.Consul.AgentService;
import dev.palazon.lvhyard.dashboard.consul.Consul.Check;
import dev.palazon.lvhyard.dashboard.consul.Consul.CheckStatus;
import dev.palazon.lvhyard.dashboard.consul.Consul.Registration;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Checks the Yard's rules. The only writer of registrations on the local agent. */
@Component
public class Registrations {

  /** A single DNS label: what a hostname's first part can be. */
  private static final Pattern DNS_LABEL = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");

  /** An IP address or a hostname. Nothing that would break a URL, such as /, ?, % or a space. */
  private static final Pattern HOST = Pattern.compile("[A-Za-z0-9.:-]+");

  /** Meta keys that LVH Yard keeps for itself. */
  static final String RESERVED_META = "lvh-yard-";

  /** Marks the registrations that are Static Instances, so nothing else is removed as one. */
  private static final Map<String, String> STATIC_INSTANCE =
      Map.of(RESERVED_META + "instance", "static");

  /**
   * Marks the registrations that are Bound Instances, which only the Container Bindings add and
   * remove.
   */
  private static final Map<String, String> BOUND_INSTANCE =
      Map.of(RESERVED_META + "instance", "bound");

  static final String CONTAINER_BINDING = RESERVED_META + "container-binding";

  /**
   * Marks a Bound Instance at its container's own address, on a container port not published on the
   * host, so only its own Workstation can reach it.
   */
  static final String UNPUBLISHED_CONTAINER_PORT = RESERVED_META + "unpublished-container-port";

  /**
   * Marks a Core Service's Static Instance, which only {@link CoreServices} registers. It is in the
   * catalog, so every Workstation reading it knows a Core Service.
   */
  private static final String CORE = RESERVED_META + "core";

  /**
   * The start of a Core Service's Static Instance ID, which is fixed, so the next start finds it:
   * {@code lvh-yard-core-<service>}.
   */
  private static final String CORE_ID_PREFIX = CORE + "-";

  /** Where an HTTP check's path is kept: Consul does not give back a check's URL. */
  static final String HTTP_PATH = RESERVED_META + "http-path";

  private static final Duration CHECK_INTERVAL = Duration.ofSeconds(10);

  private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(2);

  /** The shortest interval Consul runs a check at. */
  private static final Duration SHORTEST_INTERVAL = Duration.ofSeconds(1);

  private static final Pattern DURATION = Pattern.compile("(\\d{1,9})(ms|s|m|h)");

  /** A tag: anything without a space or a comma, so tags can be typed as a comma-separated list. */
  private static final Pattern TAG = Pattern.compile("[^\\s,]{1,255}");

  private static final Pattern META_KEY = Pattern.compile("[A-Za-z0-9_-]{1,128}");

  private static final int META_VALUE_LENGTH = 512;

  /**
   * The most meta keys an Instance can have: Consul allows 64, and LVH Yard keeps a few for itself.
   */
  private static final int META_KEYS = 60;

  /** An absolute path, with nothing that would break the check's URL, such as a space or a #. */
  private static final Pattern PATH = Pattern.compile("/[A-Za-z0-9._~!$&'()*+,;=:@%/?-]*");

  private final Workstation workstation;

  private final Consul consul;

  Registrations(Workstation workstation, Consul consul) {
    this.workstation = workstation;
    this.consul = consul;
  }

  /**
   * {@code workstation} must be this one. With no host, the Instance is at the LAN address, so
   * other Workstations reach it. With no check, it gets a TCP check on its port. The same host and
   * port again replaces that Instance, which keeps its ID.
   *
   * @return the new Instance, critical until its check first passes
   */
  public Instance register(String workstation, String service, StaticInstanceRequest request) {
    checkHome(workstation);
    checkName(service);
    StaticInstanceRequest checked = checked(request);
    String id =
        staticInstanceAt(service, checked.host(), checked.port())
            .orElseGet(() -> UUID.randomUUID().toString());
    return put(id, service, checked);
  }

  /**
   * {@code workstation} must be this one. The Instance keeps its ID.
   *
   * @return the edited Instance, critical until its check first passes
   */
  public Instance edit(
      String workstation, String service, String id, StaticInstanceRequest request) {
    checkHome(workstation);
    checkNotCore(staticInstance(service, id), "edited");
    StaticInstanceRequest checked = checked(request);
    Optional<String> other =
        staticInstanceAt(service, checked.host(), checked.port())
            .filter(found -> !found.equals(id));
    if (other.isPresent()) {
      throw new InvalidRegistrationException(
          "port",
          "Another Instance of "
              + service
              + " is already at "
              + Instance.address(checked.host(), checked.port())
              + ": edit or remove that one instead.");
    }
    return put(id, service, checked);
  }

  private StaticInstanceRequest checked(StaticInstanceRequest request) {
    checkPort(request.port());
    String host =
        request.host() == null || request.host().isBlank() ? defaultHost() : request.host().strip();
    checkHost(host);
    HealthCheck check = checked(request.check());
    checkTags(request.tags());
    checkMeta(request.meta());
    return new StaticInstanceRequest(host, request.port(), check, request.tags(), request.meta());
  }

  private Instance put(String id, String service, StaticInstanceRequest instance) {
    String host = instance.host();
    int port = instance.port();
    HealthCheck check = instance.check();
    Map<String, String> registered = new HashMap<>(instance.meta());
    registered.putAll(STATIC_INSTANCE);
    if (check.http()) {
      registered.put(HTTP_PATH, check.path());
    }
    Check consulCheck = consulCheck(host, port, check);
    consul.register(
        new Registration(id, service, host, port, instance.tags(), registered, consulCheck));
    return new Instance(
        id,
        host,
        port,
        Health.CRITICAL,
        check,
        instance.tags(),
        instance.meta(),
        List.of(new CheckResult(consulCheck.name(), Health.CRITICAL, "")),
        false,
        null,
        false,
        false);
  }

  /**
   * One already there as wanted is left alone, so its Health stays. No name rule applies: the Core
   * Service's name is kept for it.
   */
  void keepStaticInstance(CoreService service, CoreService.Location location) {
    String id = CORE_ID_PREFIX + service.serviceName();
    String host = location.host();
    int port = location.port();
    HealthCheck check = checked(new HealthCheck(HealthCheck.HTTP, service.path(), null, null));
    Check consulCheck = consulCheck(host, port, check);
    Map<String, String> meta = new HashMap<>(STATIC_INSTANCE);
    meta.put(CORE, "true");
    meta.put(HTTP_PATH, service.path());
    boolean kept =
        consul
                .agentService(id)
                .filter(found ->
                    found.name().equals(service.serviceName())
                        && found.address().equals(host)
                        && found.port() == port
                        && found.tags().isEmpty()
                        && found.meta().equals(meta))
                .isPresent()
            && hasOnly(id, consulCheck);
    if (!kept) {
      consul.register(
          new Registration(id, service.serviceName(), host, port, List.of(), meta, consulCheck));
    }
  }

  private boolean hasOnly(String id, Check wanted) {
    List<CheckStatus> checks = consul.agentChecks(id);
    return checks.size() == 1
        && checks.getFirst().name().equals(wanted.name())
        && wanted.interval().equals(checks.getFirst().interval())
        && wanted.timeout().equals(checks.getFirst().timeout());
  }

  static boolean isCore(AgentService registration) {
    return "true".equals(registration.meta().get(CORE));
  }

  /**
   * Registers each one that is missing or has moved, and removes every other one. One already there
   * as wanted is left alone, so its Health stays.
   */
  public void keepBoundInstances(List<BoundInstance> wanted) {
    Map<String, AgentService> existing = new HashMap<>();
    consul.agentServices().values().stream()
        .filter(Registrations::isBoundInstance)
        .forEach(registration -> existing.put(registration.id(), registration));
    for (BoundInstance instance : wanted) {
      AgentService found = existing.remove(instance.id());
      if (found == null
          || !found.address().equals(instance.host())
          || found.port() != instance.port()
          || !instance.binding().equals(found.meta().get(CONTAINER_BINDING))
          || instance.onHostPort() != onHostPort(found)) {
        Map<String, String> meta = new HashMap<>(BOUND_INSTANCE);
        meta.put(CONTAINER_BINDING, instance.binding());
        if (!instance.onHostPort()) {
          meta.put(UNPUBLISHED_CONTAINER_PORT, "true");
        }
        Check check = consulCheck(instance.host(), instance.port(), checked((HealthCheck) null));
        consul.register(
            new Registration(
                instance.id(),
                instance.service(),
                instance.host(),
                instance.port(),
                List.of(),
                meta,
                check));
      }
    }
    existing.keySet().forEach(consul::deregister);
  }

  public void removeBoundInstances(String service, String binding) {
    consul.agentServices().values().stream()
        .filter(registration ->
            isBoundInstance(registration)
                && registration.name().equals(service)
                && binding.equals(registration.meta().get(CONTAINER_BINDING)))
        .forEach(registration -> consul.deregister(registration.id()));
  }

  /**
   * @param onHostPort whether other Workstations can reach it: its port is published on the host,
   *     or the container is on the host's network
   */
  public record BoundInstance(
      String service,
      String binding,
      String container,
      int containerPort,
      String host,
      int port,
      boolean onHostPort) {

    /**
     * Its registration ID, the same for as long as the Binding follows a container of that name.
     * One a Binding gives that follows more than this container, such as a Compose service's, names
     * the Binding too, so a container bound both ways gives two Bound Instances, and removing one
     * Binding keeps the other's.
     */
    String id() {
      String byName = container + ":" + containerPort;
      return service + ":" + (binding.equals(byName) ? byName : binding + ":" + container);
    }
  }

  public String defaultHost() {
    return consul.agentAddress();
  }

  public static Integer parsePort(String port) {
    return parsePort("port", port);
  }

  public static Integer parsePort(String field, String port) {
    if (port == null || port.isBlank()) {
      return null;
    }
    try {
      return Integer.valueOf(port.strip());
    } catch (NumberFormatException e) {
      throw notATcpPort(field, port);
    }
  }

  /** Separated by commas, with the spaces around them dropped. */
  public static List<String> parseTags(String tags) {
    return tags == null
        ? List.of()
        : Arrays.stream(tags.split(",")).map(String::strip).filter(tag -> !tag.isEmpty()).toList();
  }

  /** One {@code key=value} on each line, with the spaces around both dropped. */
  public static Map<String, String> parseMeta(String meta) {
    Map<String, String> values = new LinkedHashMap<>();
    if (meta == null) {
      return values;
    }
    for (String line : meta.split("\\R")) {
      if (line.isBlank()) {
        continue;
      }
      int equals = line.indexOf('=');
      if (equals < 0) {
        throw new InvalidRegistrationException(
            "meta",
            "The meta line \""
                + line.strip()
                + "\" is not key=value: write one key=value on each line.");
      }
      values.put(line.substring(0, equals).strip(), line.substring(equals + 1).strip());
    }
    return values;
  }

  /** {@code workstation} must be this one. */
  public void remove(String workstation, String service, String id) {
    checkHome(workstation);
    checkNotCore(staticInstance(service, id), "removed");
    consul.deregister(id);
  }

  private static void checkNotCore(AgentService registration, String change) {
    if (isCore(registration)) {
      throw CoreServiceException.cannotBe(registration.name(), change);
    }
  }

  private AgentService staticInstance(String service, String id) {
    return consul
        .agentService(id)
        .filter(
            registration -> registration.name().equals(service) && isStaticInstance(registration))
        .orElseThrow(() -> new UnknownInstanceException(service, id));
  }

  private Optional<String> staticInstanceAt(String service, String host, int port) {
    return consul.agentServices().values().stream()
        .filter(registration ->
            registration.name().equals(service)
                && isStaticInstance(registration)
                && registration.address().equals(host)
                && registration.port() == port)
        .map(AgentService::id)
        .findFirst();
  }

  private static boolean isStaticInstance(AgentService registration) {
    return registration.meta().entrySet().containsAll(STATIC_INSTANCE.entrySet());
  }

  private static boolean isBoundInstance(AgentService registration) {
    return registration.meta().entrySet().containsAll(BOUND_INSTANCE.entrySet());
  }

  private static boolean onHostPort(AgentService registration) {
    return !registration.meta().containsKey(UNPUBLISHED_CONTAINER_PORT);
  }

  private void checkHome(String workstation) {
    if (!this.workstation.is(workstation)) {
      throw new RemoteServiceException(workstation);
    }
  }

  static void checkName(String service) {
    checkName("Service", service);
  }

  /** A Service and an Alias both take a hostname's first part. */
  static void checkName(String kind, String name) {
    if (!DNS_LABEL.matcher(name).matches()) {
      throw new InvalidRegistrationException(
          "name",
          "The "
              + kind
              + " name "
              + name
              + " is not a single DNS label: use 1 to 63 lowercase letters, digits and -, not"
              + " starting or ending with -.");
    }
    Optional<CoreService> core = CoreService.named(name);
    if (core.isPresent()) {
      throw new InvalidRegistrationException(
          "name",
          "The "
              + kind
              + " name "
              + name
              + " is taken by a Core Service: LVH Yard keeps "
              + core.get().what()
              + " at "
              + name
              + ".<base-domain>.");
    }
  }

  private static void checkHost(String host) {
    if (!HOST.matcher(host).matches()) {
      throw new InvalidRegistrationException(
          "host",
          "The host "
              + host
              + " is not an IP address or a hostname: use letters, digits, ., - and :.");
    }
  }

  private static void checkTags(List<String> tags) {
    for (String tag : tags) {
      if (tag == null || !TAG.matcher(tag).matches()) {
        throw new InvalidRegistrationException(
            "tags",
            "The tag \""
                + tag
                + "\" is not one word: use 1 to"
                + " 255 characters without spaces or commas.");
      }
    }
  }

  private static void checkMeta(Map<String, String> meta) {
    if (meta.size() > META_KEYS) {
      throw new InvalidRegistrationException(
          "meta", "An Instance can have at most " + META_KEYS + " meta keys.");
    }
    meta.forEach((key, value) -> {
      if (!META_KEY.matcher(key).matches()) {
        throw new InvalidRegistrationException(
            "meta",
            "The meta key "
                + key
                + " is not one Consul"
                + " allows: use 1 to 128 letters, digits, - and _.");
      }
      if (key.startsWith(RESERVED_META) || key.startsWith("consul-")) {
        throw new InvalidRegistrationException(
            "meta",
            "The meta key "
                + key
                + " is kept for "
                + (key.startsWith("consul-") ? "Consul" : "LVH Yard")
                + ": use another key.");
      }
      if (value != null && value.length() > META_VALUE_LENGTH) {
        throw new InvalidRegistrationException(
            "meta",
            "The meta value of "
                + key
                + " is too long: use at"
                + " most "
                + META_VALUE_LENGTH
                + " characters.");
      }
    });
  }

  private static void checkPort(Integer port) {
    checkPort("port", "A Static Instance", port);
  }

  /** {@code what} names what needs the port, for the message. */
  static void checkPort(String field, String what, Integer port) {
    if (port == null) {
      throw new InvalidRegistrationException(field, what + " needs a port.");
    }
    if (port < 1 || port > 65535) {
      throw notATcpPort(field, String.valueOf(port));
    }
  }

  private static InvalidRegistrationException notATcpPort(String port) {
    return notATcpPort("port", port);
  }

  private static InvalidRegistrationException notATcpPort(String field, String port) {
    return new InvalidRegistrationException(
        field, "The port " + port + " is not a TCP port: use 1 to 65535.");
  }

  /** With no check at all, the Instance gets a TCP check on its port. */
  private static HealthCheck checked(HealthCheck check) {
    if (check == null) {
      check = new HealthCheck(null, null, null, null);
    }
    String type =
        check.type() != null
            ? check.type()
            : check.path() != null ? HealthCheck.HTTP : HealthCheck.TCP;
    if (type.equals("script")) {
      throw new InvalidRegistrationException(
          "check", "Script checks are not offered: use an http or a tcp" + " check.");
    }
    if (!type.equals(HealthCheck.HTTP) && !type.equals(HealthCheck.TCP)) {
      throw new InvalidRegistrationException(
          "check", "The check " + type + " is not one LVH Yard offers:" + " use http or tcp.");
    }
    String path = null;
    if (type.equals(HealthCheck.HTTP)) {
      path = check.path() == null || check.path().isBlank() ? "/" : check.path();
      if (!PATH.matcher(path).matches()) {
        throw new InvalidRegistrationException(
            "path",
            "The path "
                + path
                + " is not one an HTTP check"
                + " can GET: start it with / and leave out spaces and #.");
      }
    } else if (check.path() != null && !check.path().isBlank()) {
      throw new InvalidRegistrationException(
          "path", "A TCP check has no path: use an http check to GET " + check.path() + ".");
    }
    Duration interval = duration("interval", check.interval(), CHECK_INTERVAL);
    if (interval.compareTo(SHORTEST_INTERVAL) < 0) {
      throw new InvalidRegistrationException(
          "interval",
          "The interval "
              + check.interval()
              + " is too short: a check runs at most once a second.");
    }
    Duration timeout = duration("timeout", check.timeout(), CHECK_TIMEOUT);
    return new HealthCheck(type, path, HealthCheck.format(interval), HealthCheck.format(timeout));
  }

  /** Zero is refused. */
  private static Duration duration(String field, String text, Duration otherwise) {
    if (text == null || text.isBlank()) {
      return otherwise;
    }
    Matcher matcher = DURATION.matcher(text.strip());
    if (!matcher.matches() || Long.parseLong(matcher.group(1)) == 0) {
      throw new InvalidRegistrationException(
          field,
          "The "
              + field
              + " "
              + text
              + " is not a duration: use"
              + " a whole number above 0 and ms, s, m or h, such as 10s.");
    }
    long amount = Long.parseLong(matcher.group(1));
    return switch (matcher.group(2)) {
      case "ms" -> Duration.ofMillis(amount);
      case "s" -> Duration.ofSeconds(amount);
      case "m" -> Duration.ofMinutes(amount);
      default -> Duration.ofHours(amount);
    };
  }

  private static Check consulCheck(String host, int port, HealthCheck check) {
    String address = Instance.address(host, port);
    return check.http()
        ? new Check(
            "HTTP GET " + check.path(),
            "http://" + address + check.path(),
            null,
            check.interval(),
            check.timeout())
        : new Check("TCP " + address, null, address, check.interval(), check.timeout());
  }
}
