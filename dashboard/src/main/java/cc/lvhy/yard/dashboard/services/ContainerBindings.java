package cc.lvhy.yard.dashboard.services;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.consul.ConsulKv;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * One Consul KV key per Binding, under its Service, holding the request as JSON. {@link
 * ContainerBindingRules} says what a Binding may be, and {@link ContainerSync} gives its Bound
 * Instances.
 */
@Component
public class ContainerBindings {

  static final String PREFIX = "lvh-yard/container-bindings/";

  private static final ObjectMapper JSON = JsonMapper.builder().build();

  private final Workstation workstation;

  private final ConsulKv consul;

  private final Registrations registrations;

  private final ServiceRecords records;

  private final ApplicationEventPublisher events;

  ContainerBindings(
      Workstation workstation,
      ConsulKv consul,
      Registrations registrations,
      ServiceRecords records,
      ApplicationEventPublisher events) {
    this.workstation = workstation;
    this.consul = consul;
    this.registrations = registrations;
    this.records = records;
    this.events = events;
  }

  /**
   * {@code workstation} must be this one. Running containers that match give their Bound Instances
   * at once. Any number of Services can follow one container or Compose service. Adding a Binding
   * of this Service that exists, with the same target and port, changes nothing.
   *
   * @return the Binding's ID
   * @throws UnknownServiceException when this Workstation has no Service of this name
   */
  public String add(String workstation, String service, ContainerBindingRequest request) {
    checkHome(workstation);
    InstanceRules.checkName(service);
    ContainerBindingRequest binding = ContainerBindingRules.checked(request);
    records.ensure(service);
    String id = ContainerBinding.id(binding);
    consul.putValue(PREFIX + service + "/" + id, JSON.writeValueAsString(binding));
    records.addSource(service, id);
    events.publishEvent(new ContainerBindingsChanged());
    return id;
  }

  /**
   * {@code workstation} must be this one. Changes the target and the port of a Binding. Its ID
   * holds both, so it gets a new ID, in the same place of the Instance Priority. The Bound
   * Instances of the old ID go, and the running containers that match give the new ones at once.
   *
   * @return the Binding's new ID, the old one when nothing changed
   * @throws UnknownContainerBindingException when the Service has no Binding with this ID
   * @throws InvalidRegistrationException when the Service has another Binding with the new ID
   */
  public String edit(
      String workstation, String service, String id, ContainerBindingRequest request) {
    checkHome(workstation);
    Map<String, String> stored = consul.values(null, PREFIX + service + "/");
    if (!stored.containsKey(PREFIX + service + "/" + id)) {
      throw new UnknownContainerBindingException(service, id);
    }
    ContainerBindingRequest binding = ContainerBindingRules.checked(request);
    String newId = ContainerBinding.id(binding);
    if (newId.equals(id)) {
      return id;
    }
    if (stored.containsKey(PREFIX + service + "/" + newId)) {
      throw new InvalidRegistrationException(
          "containerPort",
          "Another Binding of "
              + service
              + " already follows "
              + ContainerBindingRequest.follows(
                  binding.container(), binding.composeProject(), binding.composeService())
              + " at port "
              + binding.port()
              + ": edit or remove that one instead.");
    }
    consul.putValue(PREFIX + service + "/" + newId, JSON.writeValueAsString(binding));
    records.replaceSource(service, id, newId);
    consul.deleteValue(PREFIX + service + "/" + id);
    registrations.removeBoundInstances(service, id);
    events.publishEvent(new ContainerBindingsChanged());
    return newId;
  }

  /**
   * {@code workstation} must be this one. Removes the Bound Instances too, whether Docker answers
   * or not.
   */
  public void remove(String workstation, String service, String id) {
    checkHome(workstation);
    String key = PREFIX + service + "/" + id;
    if (!consul.values(null, PREFIX + service + "/").containsKey(key)) {
      throw new UnknownContainerBindingException(service, id);
    }
    consul.deleteValue(key);
    registrations.removeBoundInstances(service, id);
    records.removeSource(service, id);
  }

  /** Removes every Binding of {@code service} on this Workstation, with its Bound Instances. */
  void removeAll(String service) {
    all(null)
        .getOrDefault(service, Map.of())
        .keySet()
        .forEach(id -> remove(workstation.name(), service, id));
  }

  /** {@code null} reads this Workstation's own. Each Service's Bindings come by ID. */
  Map<String, Map<String, ContainerBindingRequest>> all(String datacenter) {
    Map<String, Map<String, ContainerBindingRequest>> bindings = new TreeMap<>();
    consul
        .values(datacenter, PREFIX)
        .forEach((key, value) -> {
          String[] serviceAndId = key.substring(PREFIX.length()).split("/", 2);
          if (serviceAndId.length < 2) {
            return;
          }
          try {
            bindings
                .computeIfAbsent(serviceAndId[0], service -> new TreeMap<>())
                .put(serviceAndId[1], JSON.readValue(value, ContainerBindingRequest.class));
          } catch (JacksonException e) {
            throw new IllegalStateException(
                "The Container Binding at " + key + " cannot be read: " + value, e);
          }
        });
    return bindings;
  }

  private void checkHome(String workstation) {
    if (!this.workstation.is(workstation)) {
      throw new RemoteServiceException(workstation);
    }
  }
}
