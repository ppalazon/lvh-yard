package cc.lvhy.yard.dashboard.consul;

import cc.lvhy.yard.dashboard.consul.Consul.HealthEntry;
import java.util.List;

/** The Services of a datacenter and the Health of their registrations. */
public interface ConsulCatalog {

  List<String> serviceNames(String datacenter);

  /** Each registration comes with its own checks and its node's. */
  default List<HealthEntry> health(String service) {
    return health(null, service);
  }

  List<HealthEntry> health(String datacenter, String service);
}
