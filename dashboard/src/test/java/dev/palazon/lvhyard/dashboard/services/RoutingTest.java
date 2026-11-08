package dev.palazon.lvhyard.dashboard.services;

import static dev.palazon.lvhyard.dashboard.services.Health.CRITICAL;
import static dev.palazon.lvhyard.dashboard.services.Health.PASSING;
import static dev.palazon.lvhyard.dashboard.services.InstanceKind.ALIAS;
import static dev.palazon.lvhyard.dashboard.services.InstanceKind.BOUND;
import static dev.palazon.lvhyard.dashboard.services.InstanceKind.DEFAULT_PRIORITY;
import static dev.palazon.lvhyard.dashboard.services.InstanceKind.STATIC;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The Route choice under the Instance Priority, for every case of its rule. */
class RoutingTest {

  private static int next;

  @Test
  void firstKindInTheOrderWithAPassingInstanceGetsTheRoute() {
    Instance web = staticInstance(PASSING);
    Instance bound = boundInstance(PASSING);

    Routing routing = Routing.of(DEFAULT_PRIORITY, List.of(web, bound), List.of()).orElseThrow();

    assertThat(routing.kind()).isEqualTo(STATIC);
    assertThat(routing.routedInstances()).containsExactly(web);
    assertThat(routing.waiting()).containsExactly(BOUND);
    assertThat(routing.off()).isFalse();
  }

  @Test
  void boundBeforeStaticRoutesToTheBoundInstance() {
    Instance bound = boundInstance(PASSING);

    Routing routing =
        Routing.of(List.of(BOUND, STATIC), List.of(staticInstance(PASSING), bound), List.of())
            .orElseThrow();

    assertThat(routing.kind()).isEqualTo(BOUND);
    assertThat(routing.routedInstances()).containsExactly(bound);
    assertThat(routing.waiting()).containsExactly(STATIC);
  }

  @Test
  void everyPassingInstanceOfTheChosenKindSharesTheRouteAndACriticalOneDoesNot() {
    Instance first = staticInstance(PASSING);
    Instance second = staticInstance(PASSING);

    Routing routing =
        Routing.of(DEFAULT_PRIORITY, List.of(first, second, staticInstance(CRITICAL)), List.of())
            .orElseThrow();

    assertThat(routing.routedInstances()).containsExactly(first, second);
  }

  @Test
  void kindWithNoPassingInstanceGivesTheRouteToTheNextKind() {
    Instance first = boundInstance(PASSING);
    Instance second = boundInstance(PASSING);

    Routing routing =
        Routing.of(
                DEFAULT_PRIORITY,
                List.of(staticInstance(CRITICAL), staticInstance(CRITICAL), first, second),
                List.of())
            .orElseThrow();

    assertThat(routing.kind()).isEqualTo(BOUND);
    assertThat(routing.routedInstances()).containsExactly(first, second);
    assertThat(routing.waiting()).containsExactly(STATIC);
  }

  @Test
  void kindLeftOutOfTheOrderIsNeverRoutedNorWaiting() {
    Instance bound = boundInstance(PASSING);

    Routing routing =
        Routing.of(List.of(BOUND), List.of(staticInstance(PASSING), bound), List.of())
            .orElseThrow();

    assertThat(routing.routedInstances()).containsExactly(bound);
    assertThat(routing.waiting()).isEmpty();
  }

  @Test
  void withNoPassingInstanceTheRouteUsesEveryInstanceInRange() {
    Instance web = staticInstance(CRITICAL);
    Instance bound = boundInstance(CRITICAL);

    Routing routing =
        Routing.of(DEFAULT_PRIORITY, List.of(web, bound, outOfRange(CRITICAL)), List.of())
            .orElseThrow();

    assertThat(routing.kind()).isNull();
    assertThat(routing.routedInstances()).containsExactly(web, bound);
  }

  @Test
  void outOfRangeInstanceNeverMakesItsKindWin() {
    Instance bound = boundInstance(PASSING);

    Routing routing =
        Routing.of(DEFAULT_PRIORITY, List.of(outOfRange(PASSING), bound), List.of()).orElseThrow();

    assertThat(routing.kind()).isEqualTo(BOUND);
    assertThat(routing.routedInstances()).containsExactly(bound);
    assertThat(routing.waiting()).isEmpty();
  }

  @Test
  void aliasGetsTheRouteWhenNoOwnInstancePasses() {
    Instance remote = staticInstance(PASSING);

    Routing routing =
        Routing.of(DEFAULT_PRIORITY, List.of(staticInstance(CRITICAL)), List.of(remote))
            .orElseThrow();

    assertThat(routing.kind()).isEqualTo(ALIAS);
    assertThat(routing.routedInstances()).containsExactly(remote);
    assertThat(routing.waiting()).containsExactly(STATIC);
  }

  @Test
  void aliasGetsTheRouteWhenTheServiceHasNoInstanceLeft() {
    Instance remote = staticInstance(PASSING);

    Routing routing = Routing.of(DEFAULT_PRIORITY, List.of(), List.of(remote)).orElseThrow();

    assertThat(routing.kind()).isEqualTo(ALIAS);
    assertThat(routing.routedInstances()).containsExactly(remote);
  }

  @Test
  void aliasWithNoPassingInstanceIsNeverUsed() {
    Instance web = staticInstance(CRITICAL);

    assertThat(
            Routing.of(DEFAULT_PRIORITY, List.of(web), List.of(staticInstance(CRITICAL)))
                .orElseThrow()
                .routedInstances())
        .containsExactly(web);
    assertThat(Routing.of(DEFAULT_PRIORITY, List.of(), List.of(staticInstance(CRITICAL))))
        .isEmpty();
  }

  @Test
  void offSharesTheRouteAmongPassingStaticAndBoundInstancesAndNeverUsesTheAlias() {
    Instance web = staticInstance(PASSING);
    Instance bound = boundInstance(PASSING);

    Routing routing =
        Routing.of(
                List.of(),
                List.of(web, bound, staticInstance(CRITICAL), outOfRange(PASSING)),
                List.of(staticInstance(PASSING)))
            .orElseThrow();

    assertThat(routing.off()).isTrue();
    assertThat(routing.kind()).isNull();
    assertThat(routing.routedInstances()).containsExactly(web, bound);
    assertThat(routing.offWithPassingInstances()).isTrue();
  }

  @Test
  void offWithNoPassingInstanceUsesEveryInstanceInRange() {
    Instance web = staticInstance(CRITICAL);
    Instance bound = boundInstance(CRITICAL);

    Routing routing =
        Routing.of(List.of(), List.of(web, bound, outOfRange(CRITICAL)), List.of()).orElseThrow();

    assertThat(routing.routedInstances()).containsExactly(web, bound);
    assertThat(routing.offWithPassingInstances()).isFalse();
  }

  @Test
  void nothingInRangeHasNoRoute() {
    assertThat(Routing.of(DEFAULT_PRIORITY, List.of(outOfRange(PASSING)), List.of())).isEmpty();
    assertThat(Routing.of(List.of(), List.of(outOfRange(PASSING)), List.of())).isEmpty();
  }

  private static Instance staticInstance(Health health) {
    return instance(health, false, null);
  }

  private static Instance boundInstance(Health health) {
    return instance(health, false, "binding-" + (next + 1));
  }

  /** A Static Instance that only its own Workstation reaches. */
  private static Instance outOfRange(Health health) {
    return instance(health, true, null);
  }

  private static Instance instance(Health health, boolean outOfRange, String binding) {
    next++;
    return new Instance(
        "instance-" + next,
        "10.0.0." + next,
        8080,
        health,
        null,
        List.of(),
        Map.of(),
        List.of(),
        outOfRange,
        binding,
        binding == null ? null : "container-" + next,
        binding == null ? null : 80,
        null,
        false,
        false);
  }
}
