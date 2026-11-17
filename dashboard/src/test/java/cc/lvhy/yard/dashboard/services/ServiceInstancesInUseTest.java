package cc.lvhy.yard.dashboard.services;

import static cc.lvhy.yard.dashboard.services.Health.CRITICAL;
import static cc.lvhy.yard.dashboard.services.Health.PASSING;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The Instances that the passing count of a Service shows. */
class ServiceInstancesInUseTest {

  @Test
  void onlyTheSourceInUseCountsWhileTheInstancePriorityIsOn() {
    Instance primary = instance("primary", PASSING);
    Instance waiting = instance("waiting", CRITICAL);
    InstanceSource first = new InstanceSource("first", SourceKind.STATIC, List.of(primary));
    InstanceSource second = new InstanceSource("second", SourceKind.STATIC, List.of(waiting));

    Service service = service(List.of(first, second), true);

    assertThat(service.instancesInUse()).containsExactly(primary);
    assertThat(service.countedInstances()).containsExactly(primary, waiting);
  }

  @Test
  void everyInstanceCountsWhileTheInstancePriorityIsOff() {
    Instance primary = instance("primary", PASSING);
    Instance other = instance("other", CRITICAL);
    InstanceSource first = new InstanceSource("first", SourceKind.STATIC, List.of(primary));
    InstanceSource second = new InstanceSource("second", SourceKind.STATIC, List.of(other));

    assertThat(service(List.of(first, second), false).instancesInUse())
        .containsExactly(primary, other);
  }

  @Test
  void everyInstanceCountsWhenNoSourcePasses() {
    Instance a = instance("a", CRITICAL);
    Instance b = instance("b", CRITICAL);
    InstanceSource first = new InstanceSource("first", SourceKind.STATIC, List.of(a));
    InstanceSource second = new InstanceSource("second", SourceKind.STATIC, List.of(b));

    assertThat(service(List.of(first, second), true).instancesInUse()).containsExactly(a, b);
  }

  private static Service service(List<InstanceSource> sources, boolean priority) {
    List<Instance> instances = sources.stream().flatMap(s -> s.givenInstances().stream()).toList();
    return new Service(
        "ws-a",
        "web",
        ServiceState.of(new Publication(Scope.LOCAL, List.of(), false, false), instances),
        true,
        false,
        new Publication(Scope.LOCAL, List.of(), false, false),
        "web.lvh.me",
        instances,
        List.of(),
        List.of(),
        sources,
        priority,
        List.of(),
        Map.of(),
        Routing.of(sources, priority, true).orElseThrow());
  }

  private static Instance instance(String id, Health health) {
    return new Instance(
        id,
        "10.0.0.1",
        8080,
        health,
        null,
        List.of(),
        Map.of(),
        List.of(),
        false,
        null,
        null,
        null,
        null,
        false,
        false);
  }
}
