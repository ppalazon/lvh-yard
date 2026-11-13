package cc.lvhy.yard.dashboard.services;

import static cc.lvhy.yard.dashboard.services.Health.CRITICAL;
import static cc.lvhy.yard.dashboard.services.Health.PASSING;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The Route choice over the ordered Instance Sources, for every case of its rule. */
class RoutingTest {

  private static final boolean ON = true;

  private static final boolean OFF = false;

  private static final boolean HOME = true;

  private static int next;

  @Test
  void firstSourceWithAPassingInstanceGetsTheRoute() {
    Instance first = instance(PASSING);
    Instance second = instance(PASSING);
    InstanceSource a = source(SourceKind.STATIC, first);
    InstanceSource b = source(SourceKind.STATIC, second);

    Routing routing = Routing.of(List.of(a, b), ON, HOME).orElseThrow();

    assertThat(routing.source()).isEqualTo(a.id());
    assertThat(routing.kind()).isEqualTo(SourceKind.STATIC);
    assertThat(routing.routedInstances()).containsExactly(first);
    assertThat(routing.waiting()).containsExactly(b.id());
    assertThat(routing.instances()).containsExactly(first.id());
    assertThat(routing.off()).isFalse();
    assertThat(routing.offWithPassingInstances()).isFalse();
  }

  @Test
  void theOrderDecidesAndNotTheKind() {
    Instance web = instance(PASSING);
    InstanceSource binding = source(SourceKind.BOUND, instance(PASSING));
    InstanceSource staticSource = source(SourceKind.STATIC, web);

    Routing routing = Routing.of(List.of(binding, staticSource), ON, HOME).orElseThrow();

    assertThat(routing.source()).isEqualTo(binding.id());
    assertThat(routing.waiting()).containsExactly(staticSource.id());
  }

  @Test
  void everyPassingInstanceOfTheChosenSourceSharesTheRouteAndACriticalOneDoesNot() {
    Instance first = instance(PASSING);
    Instance second = instance(PASSING);
    InstanceSource binding = source(SourceKind.BOUND, first, second, instance(CRITICAL));

    Routing routing = Routing.of(List.of(binding), ON, HOME).orElseThrow();

    assertThat(routing.routedInstances()).containsExactly(first, second);
  }

  @Test
  void sourceWithNoPassingInstanceGivesTheRouteToTheNextSource() {
    Instance passing = instance(PASSING);
    InstanceSource failing = source(SourceKind.STATIC, instance(CRITICAL));
    InstanceSource next = source(SourceKind.BOUND, passing);

    Routing routing = Routing.of(List.of(failing, next), ON, HOME).orElseThrow();

    assertThat(routing.source()).isEqualTo(next.id());
    assertThat(routing.routedInstances()).containsExactly(passing);
    assertThat(routing.waiting()).containsExactly(failing.id());
  }

  @Test
  void sourceWithNoInstancesIsSkippedAndNeverWaits() {
    Instance web = instance(PASSING);
    InstanceSource empty = source(SourceKind.BOUND);
    InstanceSource staticSource = source(SourceKind.STATIC, web);

    Routing routing = Routing.of(List.of(empty, staticSource), ON, HOME).orElseThrow();

    assertThat(routing.source()).isEqualTo(staticSource.id());
    assertThat(routing.waiting()).isEmpty();
  }

  @Test
  void outOfRangeInstanceNeverMakesItsSourceWinNorWait() {
    Instance web = instance(PASSING);
    InstanceSource far = source(SourceKind.STATIC, outOfRange(PASSING));
    InstanceSource near = source(SourceKind.STATIC, web);

    Routing routing = Routing.of(List.of(far, near), ON, HOME).orElseThrow();

    assertThat(routing.source()).isEqualTo(near.id());
    assertThat(routing.routedInstances()).containsExactly(web);
    assertThat(routing.waiting()).isEmpty();
  }

  @Test
  void withNoPassingInstanceTheRouteUsesEveryInstanceInRange() {
    Instance web = instance(CRITICAL);
    Instance bound = instance(CRITICAL);
    Instance remote = instance(CRITICAL);

    Routing routing =
        Routing.of(
                List.of(
                    source(SourceKind.STATIC, web),
                    source(SourceKind.BOUND, bound, outOfRange(CRITICAL)),
                    source(SourceKind.ALIAS, remote)),
                ON,
                HOME)
            .orElseThrow();

    assertThat(routing.source()).isNull();
    assertThat(routing.kind()).isNull();
    assertThat(routing.routedInstances()).containsExactly(web, bound, remote);
    assertThat(routing.waiting()).isEmpty();
  }

  @Test
  void aliasGetsTheRouteInItsPlaceOfTheOrder() {
    Instance remote = instance(PASSING);
    InstanceSource alias = source(SourceKind.ALIAS, remote);
    InstanceSource staticSource = source(SourceKind.STATIC, instance(PASSING));

    Routing routing = Routing.of(List.of(alias, staticSource), ON, HOME).orElseThrow();

    assertThat(routing.source()).isEqualTo(alias.id());
    assertThat(routing.kind()).isEqualTo(SourceKind.ALIAS);
    assertThat(routing.routedInstances()).containsExactly(remote);
  }

  @Test
  void anotherWorkstationSkipsAliasSources() {
    Instance web = instance(PASSING);
    InstanceSource alias = source(SourceKind.ALIAS, instance(PASSING));
    InstanceSource staticSource = source(SourceKind.STATIC, web);

    Routing routing = Routing.of(List.of(alias, staticSource), ON, !HOME).orElseThrow();

    assertThat(routing.source()).isEqualTo(staticSource.id());
    assertThat(routing.waiting()).isEmpty();
    assertThat(Routing.of(List.of(alias), ON, !HOME)).isEmpty();
    assertThat(Routing.of(List.of(alias), OFF, !HOME)).isEmpty();
  }

  @Test
  void offSharesTheRouteAmongThePassingInstancesOfEverySourceAliasesIncluded() {
    Instance web = instance(PASSING);
    Instance bound = instance(PASSING);
    Instance remote = instance(PASSING);

    Routing routing =
        Routing.of(
                List.of(
                    source(SourceKind.STATIC, web, instance(CRITICAL), outOfRange(PASSING)),
                    source(SourceKind.BOUND, bound),
                    source(SourceKind.ALIAS, remote)),
                OFF,
                HOME)
            .orElseThrow();

    assertThat(routing.off()).isTrue();
    assertThat(routing.source()).isNull();
    assertThat(routing.routedInstances()).containsExactly(web, bound, remote);
    assertThat(routing.waiting()).isEmpty();
    assertThat(routing.offWithPassingInstances()).isTrue();
  }

  @Test
  void offWithNoPassingInstanceUsesEveryInstanceInRange() {
    Instance web = instance(CRITICAL);
    Instance bound = instance(CRITICAL);

    Routing routing =
        Routing.of(
                List.of(
                    source(SourceKind.STATIC, web, outOfRange(CRITICAL)),
                    source(SourceKind.BOUND, bound)),
                OFF,
                HOME)
            .orElseThrow();

    assertThat(routing.routedInstances()).containsExactly(web, bound);
    assertThat(routing.offWithPassingInstances()).isFalse();
  }

  @Test
  void nothingInRangeHasNoRoute() {
    InstanceSource far = source(SourceKind.STATIC, outOfRange(PASSING));

    assertThat(Routing.of(List.of(far, source(SourceKind.BOUND)), ON, HOME)).isEmpty();
    assertThat(Routing.of(List.of(far), OFF, HOME)).isEmpty();
    assertThat(Routing.of(List.of(), ON, HOME)).isEmpty();
  }

  @Test
  void routeMembershipUsesTheInstanceIdAndExcludesUnselectedInstances() {
    Instance web = instance(PASSING);
    Instance critical = instance(CRITICAL);
    Instance waiting = instance(PASSING);
    Routing routing =
        Routing.of(
                List.of(
                    source(SourceKind.STATIC, web, critical), source(SourceKind.BOUND, waiting)),
                ON,
                HOME)
            .orElseThrow();

    Instance sameRegistration = instance(web.id(), CRITICAL, false);
    assertThat(routing.routes(sameRegistration)).isTrue();
    assertThat(routing.routes(critical)).isFalse();
    assertThat(routing.routes(waiting)).isFalse();
    assertThat(routing.routes(instance(PASSING))).isFalse();
  }

  private static InstanceSource source(SourceKind kind, Instance... instances) {
    next++;
    return new InstanceSource("source-" + next, kind, List.of(instances));
  }

  private static Instance instance(Health health) {
    return instance(health, false);
  }

  /** An Instance that only its own Workstation reaches. */
  private static Instance outOfRange(Health health) {
    return instance(health, true);
  }

  private static Instance instance(Health health, boolean outOfRange) {
    return instance("instance-" + ++next, health, outOfRange);
  }

  private static Instance instance(String id, Health health, boolean outOfRange) {
    return new Instance(
        id,
        "10.0.0." + next,
        8080,
        health,
        null,
        List.of(),
        Map.of(),
        List.of(),
        outOfRange,
        null,
        null,
        null,
        null,
        false,
        false);
  }
}
