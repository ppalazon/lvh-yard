package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.consul.Consul;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * One Consul KV key per Service that you created, holding a JSON object. The key alone makes the
 * Service exist, with no Instances and no Publication. It has no other parts yet.
 */
@Component
public class ServiceRecords {

  private static final String PREFIX = "lvh-yard/services/";

  private static final ObjectMapper JSON = JsonMapper.builder().build();

  private final Consul consul;

  ServiceRecords(Consul consul) {
    this.consul = consul;
  }

  /** Writes a new record, or keeps the one there. */
  void put(String service) {
    if (!exists(service)) {
      consul.putValue(PREFIX + service, "{}");
    }
  }

  boolean exists(String service) {
    return consul.values(null, PREFIX + service).containsKey(PREFIX + service);
  }

  void delete(String service) {
    consul.deleteValue(PREFIX + service);
  }

  /** {@code null} reads this Workstation's own. */
  Map<String, ServiceRecord> all(String datacenter) {
    Map<String, ServiceRecord> records = new TreeMap<>();
    consul
        .values(datacenter, PREFIX)
        .forEach((key, value) -> {
          String service = key.substring(PREFIX.length());
          if (service.isEmpty() || service.contains("/")) {
            return;
          }
          try {
            records.put(service, JSON.readValue(value, ServiceRecord.class));
          } catch (JacksonException e) {
            throw new IllegalStateException(
                "The Service record at " + key + " cannot be read: " + value, e);
          }
        });
    return records;
  }

  /** What the record of a Service holds: nothing yet, as JSON, so that it can get parts. */
  record ServiceRecord() {}
}
