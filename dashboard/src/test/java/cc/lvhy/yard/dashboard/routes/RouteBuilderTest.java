package cc.lvhy.yard.dashboard.routes;

import static org.assertj.core.api.Assertions.assertThat;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.profiles.RouteProfileDefinition;
import cc.lvhy.yard.dashboard.services.Health;
import cc.lvhy.yard.dashboard.services.Instance;
import cc.lvhy.yard.dashboard.services.Publication;
import cc.lvhy.yard.dashboard.services.Routing;
import cc.lvhy.yard.dashboard.services.Scope;
import cc.lvhy.yard.dashboard.services.Service;
import cc.lvhy.yard.dashboard.services.ServiceState;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The Traefik middlewares built from the Route Profiles of Services. */
class RouteBuilderTest {

  @Test
  void profilesOfWorkstationsWhoseNamesJoinTheSameWayNeverClash() {
    Service first = serviceWithProfile("a", "first", "b-c", "alice");
    Service second = serviceWithProfile("a-b", "second", "c", "bob");

    TraefikConfiguration configuration =
        RouteBuilder.build(new Workstation("a", "a.lvh.me"), List.of(first, second));

    var middlewares = configuration.http().middlewares();
    assertThat(middlewares).hasSize(2);
    assertThat(middlewares.get("a_b-c").toString()).contains("alice");
    assertThat(middlewares.get("a-b_c").toString()).contains("bob");
    assertThat(configuration.http().routers().get("first").middlewares()).containsExactly("a_b-c");
    assertThat(configuration.http().routers().get("second.a-b").middlewares())
        .containsExactly("a-b_c");
  }

  private static Service serviceWithProfile(
      String workstation, String name, String profile, String user) {
    Instance instance =
        new Instance(
            name,
            "10.1.2.3",
            8080,
            Health.PASSING,
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
    Map<String, Map<String, Object>> middlewares =
        Map.of(profile, Map.of("basicAuth", Map.of("users", List.of(user + ":hash"))));
    return new Service(
        workstation,
        name,
        ServiceState.HEALTHY,
        true,
        false,
        new Publication(Scope.YARD, List.of(profile), true),
        name + "." + workstation + ".lvh.me",
        List.of(instance),
        List.of(),
        List.of(),
        List.of(),
        true,
        List.of(profile),
        Map.of(profile, new RouteProfileDefinition(profile, middlewares)),
        new Routing(null, List.of(instance), List.of(), true));
  }
}
