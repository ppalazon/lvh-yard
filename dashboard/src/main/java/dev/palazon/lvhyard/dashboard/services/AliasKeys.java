package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.consul.Consul;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * One Consul KV key per Alias, holding {@code <workstation>/<service>}. Apart from {@link Aliases},
 * so {@link YardReader} can read the targets without depending on {@link Aliases}, which reads the
 * Yard.
 */
@Component
class AliasKeys {

  private static final String PREFIX = "lvh-yard/aliases/";

  private final Consul consul;

  AliasKeys(Consul consul) {
    this.consul = consul;
  }

  /** The Remote Service of each Alias of this Workstation, by the Alias's name. */
  Map<String, Target> targets() {
    Map<String, Target> targets = new TreeMap<>();
    consul
        .values(null, PREFIX)
        .forEach((key, value) -> {
          String[] target = value.split("/", 2);
          targets.put(
              key.substring(PREFIX.length()),
              new Target(target[0], target.length < 2 ? "" : target[1]));
        });
    return targets;
  }

  /** Also points an existing Alias there. */
  void put(String name, Target target) {
    consul.putValue(PREFIX + name, target.workstation() + "/" + target.service());
  }

  boolean contains(String name) {
    return consul.values(null, PREFIX).containsKey(PREFIX + name);
  }

  void delete(String name) {
    consul.deleteValue(PREFIX + name);
  }

  record Target(String workstation, String service) {

    /** The Instance Source ID of the Alias. */
    String id() {
      return workstation + "/" + service;
    }
  }
}
