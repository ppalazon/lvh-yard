package dev.palazon.lvhyard.dashboard.status;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.CoreService;
import dev.palazon.lvhyard.dashboard.services.Health;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.Service;
import dev.palazon.lvhyard.dashboard.services.YardReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The Health of each of this Workstation's Core Services, from the catalog: whether the dashboard,
 * Consul's web page and Traefik's admin page answer through their checks. A Core Service with no
 * Instance is failing.
 */
@Component
@Order(StatusOrder.CORE_SERVICES)
class CoreServicesStatus implements PartReader {

  static final String NAME = "Core Services";

  private final YardReader yard;

  private final Workstation workstation;

  CoreServicesStatus(YardReader yard, Workstation workstation) {
    this.yard = yard;
    this.workstation = workstation;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Part read() {
    List<Part.Detail> details = new ArrayList<>();
    List<String> notPassing = new ArrayList<>();
    for (CoreService core : CoreService.values()) {
      Optional<Instance> best =
          yard.service(workstation.name(), core.serviceName()).map(Service::instances).stream()
              .flatMap(List::stream)
              .min(Comparator.comparing(Instance::health));
      if (best.isEmpty()) {
        details.add(new Part.Detail(core.serviceName(), "missing", PartState.FAILING));
        notPassing.add(core.what() + " (missing)");
        continue;
      }
      Instance instance = best.get();
      details.add(
          new Part.Detail(
              core.serviceName(),
              instance.health().value() + " at " + instance.address(),
              state(instance.health())));
      if (instance.health() != Health.PASSING) {
        notPassing.add(core.what() + " (" + instance.health().value() + ")");
      }
    }
    return Part.of(NAME, reason(notPassing), details);
  }

  private static String reason(List<String> notPassing) {
    if (notPassing.isEmpty()) {
      return "Each Core Service passes its check.";
    }
    return "Not passing its check: " + String.join(", ", notPassing) + ".";
  }

  private static PartState state(Health health) {
    return switch (health) {
      case PASSING -> PartState.OK;
      case WARNING -> PartState.WARNING;
      case CRITICAL -> PartState.FAILING;
    };
  }
}
