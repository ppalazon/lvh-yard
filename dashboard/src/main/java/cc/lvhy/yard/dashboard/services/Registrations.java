package cc.lvhy.yard.dashboard.services;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.consul.Consul;
import cc.lvhy.yard.dashboard.consul.Consul.AgentService;
import cc.lvhy.yard.dashboard.consul.Consul.Check;
import cc.lvhy.yard.dashboard.consul.Consul.Registration;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Checks the Yard's rules. The only writer of registrations on the local agent. */
@Component
public class Registrations {

  /** Marks the registrations that are Static Instances, so nothing else is removed as one. */
  private static final Map<String, String> STATIC_INSTANCE =
      Map.of(InstanceRules.RESERVED_META + "instance", "static");

  /**
   * Marks the registrations that are Bound Instances, which only the Container Bindings add and
   * remove.
   */
  private static final Map<String, String> BOUND_INSTANCE =
      Map.of(InstanceRules.RESERVED_META + "instance", "bound");

  static final String CONTAINER_BINDING = InstanceRules.RESERVED_META + "container-binding";

  static final String CONTAINER = InstanceRules.RESERVED_META + "container";

  static final String CONTAINER_PORT = InstanceRules.RESERVED_META + "container-port";

  /**
   * Marks a Bound Instance at its container's own address, on a container port not published on the
   * host, so only its own Workstation can reach it.
   */
  static final String UNPUBLISHED_CONTAINER_PORT =
      InstanceRules.RESERVED_META + "unpublished-container-port";

  /** Where an HTTP check's path is kept: Consul does not give back a check's URL. */
  static final String HTTP_PATH = InstanceRules.RESERVED_META + "http-path";

  private final Workstation workstation;

  private final Consul consul;

  private final ServiceRecords records;

  /** The check interval of every Bound Instance. Only the tests change it, so they wait less. */
  private final Duration boundCheckInterval;

  Registrations(
      Workstation workstation,
      Consul consul,
      ServiceRecords records,
      @Value("${lvh-yard.bound-instances.check-interval:10s}") Duration boundCheckInterval) {
    this.workstation = workstation;
    this.consul = consul;
    this.records = records;
    this.boundCheckInterval = boundCheckInterval;
  }

  /**
   * {@code workstation} must be this one. With no host, the Instance is at the LAN address, so
   * other Workstations reach it. With no check, it gets a TCP check on its port. The same host and
   * port again replaces that Instance, which keeps its ID.
   *
   * @return the new Instance, critical until its check first passes
   * @throws UnknownServiceException when this Workstation has no Service of this name
   */
  public Instance register(String workstation, String service, StaticInstanceRequest request) {
    checkHome(workstation);
    InstanceRules.checkName(service);
    StaticInstanceRequest checked = checked(request);
    records.ensure(service);
    String id =
        staticInstanceAt(service, checked.host(), checked.port())
            .orElseGet(() -> UUID.randomUUID().toString());
    Instance registered = put(id, service, checked);
    records.addSource(service, id);
    return registered;
  }

  /**
   * {@code workstation} must be this one. The Instance keeps its ID.
   *
   * @return the edited Instance, critical until its check first passes
   */
  public Instance edit(
      String workstation, String service, String id, StaticInstanceRequest request) {
    checkHome(workstation);
    requireStaticInstance(service, id);
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

  /**
   * The Static Instance as {@link #register} would store it, with the default host and check filled
   * in. It writes nothing.
   *
   * @throws InvalidRegistrationException at the first rule that {@code request} breaks
   */
  public StaticInstanceRequest check(String service, StaticInstanceRequest request) {
    InstanceRules.checkName(service);
    return checked(request);
  }

  private StaticInstanceRequest checked(StaticInstanceRequest request) {
    return InstanceRules.checked(request, this::defaultHost);
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
        null,
        null,
        null,
        false,
        false);
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
          || !instance.container().equals(found.meta().get(CONTAINER))
          || !String.valueOf(instance.containerPort()).equals(found.meta().get(CONTAINER_PORT))
          || instance.onHostPort() != onHostPort(found)) {
        Map<String, String> meta = new HashMap<>(BOUND_INSTANCE);
        meta.put(CONTAINER_BINDING, instance.binding());
        meta.put(CONTAINER, instance.container());
        meta.put(CONTAINER_PORT, String.valueOf(instance.containerPort()));
        if (!instance.onHostPort()) {
          meta.put(UNPUBLISHED_CONTAINER_PORT, "true");
        }
        Check check =
            consulCheck(
                instance.host(),
                instance.port(),
                InstanceRules.check(
                    new HealthCheck(null, null, HealthCheck.format(boundCheckInterval), null)));
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

  /** Removes every Static Instance of {@code service} on this Workstation. */
  void removeStaticInstances(String service) {
    consul.agentServices().values().stream()
        .filter(
            registration -> registration.name().equals(service) && isStaticInstance(registration))
        .forEach(registration -> consul.deregister(registration.id()));
  }

  /** {@code workstation} must be this one. */
  public void remove(String workstation, String service, String id) {
    checkHome(workstation);
    requireStaticInstance(service, id);
    consul.deregister(id);
    records.removeSource(service, id);
  }

  private void requireStaticInstance(String service, String id) {
    consul
        .agentService(id)
        .filter(
            registration -> registration.name().equals(service) && isStaticInstance(registration))
        .orElseThrow(() -> new UnknownInstanceException(service, id));
  }

  /** The ID of the Static Instance of {@code service} at this host and port. */
  public Optional<String> staticInstanceAt(String service, String host, int port) {
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
    return isStaticInstance(registration.meta());
  }

  static boolean isStaticInstance(Map<String, String> meta) {
    return meta.entrySet().containsAll(STATIC_INSTANCE.entrySet());
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
