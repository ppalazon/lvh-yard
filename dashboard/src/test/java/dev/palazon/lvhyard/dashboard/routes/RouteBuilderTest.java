package dev.palazon.lvhyard.dashboard.routes;

import static org.assertj.core.api.Assertions.assertThat;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.profiles.RouteProfileDefinition;
import dev.palazon.lvhyard.dashboard.services.Health;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.Routing;
import dev.palazon.lvhyard.dashboard.services.Service;
import dev.palazon.lvhyard.dashboard.services.ServiceState;
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
        RouteBuilder.build(
            new Workstation("a", "a.lvh.me"),
            "http://dashboard:8080",
            List.of(first, second),
            List.of());

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
            false,
            false);
    Map<String, Map<String, Object>> middlewares =
        Map.of(profile, Map.of("basicAuth", Map.of("users", List.of(user + ":hash"))));
    return new Service(
        workstation,
        name,
        ServiceState.HEALTHY,
        null,
        name + "." + workstation + ".lvh.me",
        List.of(instance),
        List.of(),
        List.of(),
        null,
        List.of(profile),
        Map.of(profile, new RouteProfileDefinition(profile, middlewares)),
        new Routing(null, List.of(instance), List.of(), true),
        false);
  }
}
