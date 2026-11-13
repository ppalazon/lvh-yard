package cc.lvhy.yard.dashboard.services;

import cc.lvhy.yard.dashboard.consul.Consul;
import cc.lvhy.yard.dashboard.consul.ConsulCatalog;
import cc.lvhy.yard.dashboard.consul.ConsulKv;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * One Consul KV key per Service, holding a JSON object. The key alone makes the Service exist, with
 * no Instances and no Publication. A Service that only Foreign Instances give has none until a
 * change needs it. It holds the Instance Priority (the order of the Instance Source IDs and whether
 * the order is on) and the Aliases of the Service.
 */
@Component
public class ServiceRecords {

  private static final String PREFIX = "lvh-yard/services/";

  private static final ObjectMapper JSON = JsonMapper.builder().build();

  private final ConsulKv kv;

  private final ConsulCatalog catalog;

  ServiceRecords(ConsulKv kv, ConsulCatalog catalog) {
    this.kv = kv;
    this.catalog = catalog;
  }

  /** Writes a new record, or keeps the one there. */
  void put(String service) {
    if (!exists(service)) {
      write(service, new ServiceRecord(List.of(), null, List.of()));
    }
  }

  boolean exists(String service) {
    return get(service).isPresent();
  }

  /**
   * Whether this Workstation has the Service: a record, or an Instance that another tool
   * registered.
   */
  boolean serviceExists(String service) {
    return exists(service)
        || catalog.health(service).stream()
            .anyMatch(entry -> !Consul.isServerItself(entry.service()));
  }

  /**
   * Writes the record of a Service that only Foreign Instances give, before a write that needs it.
   *
   * @throws UnknownServiceException when this Workstation has no Service of this name
   */
  void ensure(String service) {
    if (exists(service)) {
      return;
    }
    if (!serviceExists(service)) {
      throw new UnknownServiceException(service);
    }
    write(service, new ServiceRecord(List.of(), null, List.of()));
  }

  Optional<ServiceRecord> get(String service) {
    return Optional.ofNullable(kv.values(null, PREFIX + service).get(PREFIX + service))
        .map(value -> read(PREFIX + service, value));
  }

  void delete(String service) {
    kv.deleteValue(PREFIX + service);
  }

  /**
   * Puts a new Instance Source at the end of the order, so it never takes over the Route. Sources
   * that the order does not name yet, such as new Foreign Instances, keep their place before it. A
   * source that the order already names keeps its place, so an edit leaves the Route alone.
   */
  void addSource(String service, String id) {
    get(service)
        .filter(stored -> !stored.order().contains(id))
        .ifPresent(stored -> {
          Set<String> others = new HashSet<>(sourceIds(service));
          others.remove(id);
          List<String> order = new ArrayList<>(InstanceSource.order(stored.order(), others));
          order.add(id);
          write(service, new ServiceRecord(order, stored.priorityOff(), stored.aliases()));
        });
  }

  /**
   * Adds an Alias at the end of the order.
   *
   * @param id {@code <workstation>/<service>} of its Remote Service
   * @throws UnknownServiceException when the Service has no record
   * @throws AliasExistsException when the Service already has an Alias to this Remote Service
   */
  void addAlias(String service, String id) {
    ServiceRecord stored = get(service).orElseThrow(() -> new UnknownServiceException(service));
    if (stored.aliases().contains(id)) {
      throw new AliasExistsException(service, id);
    }
    List<String> aliases = new ArrayList<>(stored.aliases());
    aliases.add(id);
    write(service, new ServiceRecord(stored.order(), stored.priorityOff(), aliases));
    addSource(service, id);
  }

  /**
   * @throws UnknownServiceException when the Service has no record
   * @throws UnknownAliasException when the Service has no Alias to this Remote Service
   */
  void removeAlias(String service, String id) {
    ServiceRecord stored = get(service).orElseThrow(() -> new UnknownServiceException(service));
    if (!stored.aliases().contains(id)) {
      throw new UnknownAliasException(service, id);
    }
    write(
        service,
        new ServiceRecord(
            stored.order().stream().filter(other -> !other.equals(id)).toList(),
            stored.priorityOff(),
            stored.aliases().stream().filter(other -> !other.equals(id)).toList()));
  }

  /** Takes a removed Instance Source out of the order. */
  void removeSource(String service, String id) {
    get(service)
        .filter(stored -> stored.order().contains(id))
        .ifPresent(stored ->
            write(
                service,
                new ServiceRecord(
                    stored.order().stream().filter(other -> !other.equals(id)).toList(),
                    stored.priorityOff(),
                    stored.aliases())));
  }

  /**
   * Gives a source that changed its ID the place of its old ID, so an edit leaves the Route alone.
   * An old ID that the order does not name leaves the order as it is.
   */
  void replaceSource(String service, String oldId, String newId) {
    get(service)
        .filter(stored -> stored.order().contains(oldId))
        .ifPresent(stored -> {
          List<String> order = new ArrayList<>();
          for (String id : stored.order()) {
            if (id.equals(oldId)) {
              order.add(newId);
            } else if (!id.equals(newId)) {
              order.add(id);
            }
          }
          write(service, new ServiceRecord(order, stored.priorityOff(), stored.aliases()));
        });
  }

  /**
   * Sets the order, the toggle or both. The IDs that {@code order} names come first, and the other
   * sources follow in their order. IDs whose sources are gone are dropped.
   *
   * @param order {@code null} keeps the order
   * @param on {@code null} keeps the toggle
   * @throws UnknownServiceException when the Service has no record
   * @throws InvalidPriorityException when {@code order} names an ID twice, or an ID that is not one
   *     of the Service's Instance Sources
   */
  void setPriority(String service, List<String> order, Boolean on) {
    ServiceRecord stored = get(service).orElseThrow(() -> new UnknownServiceException(service));
    List<String> current = InstanceSource.order(stored.order(), sourceIds(service));
    List<String> chosen = new ArrayList<>();
    for (String id : order == null ? List.<String>of() : order) {
      if (chosen.contains(id)) {
        throw new InvalidPriorityException("The order names " + id + " more than once.");
      }
      if (!current.contains(id)) {
        throw new InvalidPriorityException(
            service
                + " has no Instance Source "
                + id
                + ". Its Instance Sources are: "
                + (current.isEmpty() ? "none" : String.join(", ", current))
                + ".");
      }
      chosen.add(id);
    }
    current.stream().filter(id -> !chosen.contains(id)).forEach(chosen::add);
    write(
        service,
        new ServiceRecord(
            chosen, on == null ? stored.priorityOff() : (on ? null : true), stored.aliases()));
  }

  /**
   * The IDs of this Workstation's Instance Sources of {@code service} now: its Static and Foreign
   * Instances, its Container Bindings and its Aliases.
   */
  private Set<String> sourceIds(String service) {
    Set<String> ids = new HashSet<>();
    catalog.health(service).stream()
        .map(Consul.HealthEntry::service)
        .filter(registration -> !Consul.isServerItself(registration))
        .filter(registration -> !registration.meta().containsKey(Registrations.CONTAINER_BINDING))
        .forEach(registration -> ids.add(registration.id()));
    kv.values(null, ContainerBindings.PREFIX + service + "/").keySet().stream()
        .map(key -> key.substring((ContainerBindings.PREFIX + service + "/").length()))
        .filter(id -> !id.isEmpty() && !id.contains("/"))
        .forEach(ids::add);
    get(service).ifPresent(stored -> ids.addAll(stored.aliases()));
    return ids;
  }

  /** {@code null} reads this Workstation's own. */
  Map<String, ServiceRecord> all(String datacenter) {
    Map<String, ServiceRecord> records = new TreeMap<>();
    kv.values(datacenter, PREFIX)
        .forEach((key, value) -> {
          String service = key.substring(PREFIX.length());
          if (!service.isEmpty() && !service.contains("/")) {
            records.put(service, read(key, value));
          }
        });
    return records;
  }

  private void write(String service, ServiceRecord record) {
    kv.putValue(PREFIX + service, JSON.writeValueAsString(record));
  }

  private static ServiceRecord read(String key, String value) {
    try {
      return JSON.readValue(value, ServiceRecord.class);
    } catch (JacksonException e) {
      throw new IllegalStateException(
          "The Service record at " + key + " cannot be read: " + value, e);
    }
  }

  /**
   * @param order the Instance Source IDs in order. It can name sources that are gone, and miss new
   *     ones.
   * @param priorityOff {@code true} when the Instance Priority is off. It is left out while on, so
   *     a new record is {@code {}}.
   * @param aliases the Instance Source IDs of its Aliases, {@code <workstation>/<service>} of each
   *     Remote Service, in the order they were added. Only this Workstation reads them.
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  record ServiceRecord(List<String> order, Boolean priorityOff, List<String> aliases) {

    ServiceRecord {
      order = order == null ? List.of() : List.copyOf(order);
      aliases = aliases == null ? List.of() : List.copyOf(aliases);
      priorityOff = Boolean.TRUE.equals(priorityOff) ? true : null;
    }

    boolean instancePriority() {
      return priorityOff == null;
    }
  }
}
