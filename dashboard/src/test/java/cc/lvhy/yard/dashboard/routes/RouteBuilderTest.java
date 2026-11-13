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
        RouteBuilder.build(new Workstation("a", "a.lvh.me"), List.of(first, second), false);

    var middlewares = configuration.http().middlewares();
    assertThat(middlewares).hasSize(2);
    assertThat(middlewares.get("a_b-c").toString()).contains("alice");
    assertThat(middlewares.get("a-b_c").toString()).contains("bob");
    assertThat(configuration.http().routers().get("first").middlewares()).containsExactly("a_b-c");
    assertThat(configuration.http().routers().get("second.a-b").middlewares())
        .containsExactly("a-b_c");
  }

  @Test
  void plainHttpGivesOnePlainRouterAndHttpsAddsASecureOneBesideIt() {
    Service web = service("a", "web", List.of(), Map.of(), true);
    Workstation workstation = new Workstation("a", "a.lvh.me");

    var plain = RouteBuilder.build(workstation, List.of(web), false).http();
    var secure = RouteBuilder.build(workstation, List.of(web), true).http();

    assertThat(plain.routers()).containsOnlyKeys("web");
    assertThat(plain.routers().get("web").entryPoints()).containsExactly("web");
    assertThat(plain.routers().get("web").rule()).isEqualTo("Host(`web.a.lvh.me`)");
    assertThat(secure.routers()).containsOnlyKeys("web", "web__https");
    var router = secure.routers().get("web__https");
    assertThat(router.entryPoints()).containsExactly("websecure");
    assertThat(router.service()).isEqualTo("web");
    assertThat(router.rule()).isEqualTo("Host(`web.a.lvh.me`)");
    assertThat(router.tls()).isEmpty();
  }

  @Test
  void theBackendHasOneServerForEachRoutedInstance() {
    Service web = service("a", "web", List.of(), Map.of(), true);

    var http = RouteBuilder.build(new Workstation("a", "a.lvh.me"), List.of(web), false).http();

    assertThat(http.services().get("web").loadBalancer().servers())
        .extracting(TraefikConfiguration.Server::url)
        .containsExactly("http://10.1.2.3:8080");
  }

  @Test
  void aRemoteServiceIsNamedAfterItsHomeWorkstation() {
    Service web = service("b", "web", List.of(), Map.of(), true);

    var http = RouteBuilder.build(new Workstation("a", "a.lvh.me"), List.of(web), false).http();

    assertThat(http.routers()).containsOnlyKeys("web.b");
    assertThat(http.services()).containsOnlyKeys("web.b");
  }

  @Test
  void theAccessLogSwitchDecidesWhetherTraefikWritesTheLog() {
    Workstation workstation = new Workstation("a", "a.lvh.me");
    Service logged = service("a", "logged", List.of(), Map.of(), true);
    Service quiet = service("a", "quiet", List.of(), Map.of(), false);

    var http = RouteBuilder.build(workstation, List.of(logged, quiet), true).http();

    assertThat(http.routers().get("logged").observability()).isNull();
    assertThat(http.routers().get("logged__https").observability()).isNull();
    assertThat(http.routers().get("quiet").observability())
        .isEqualTo(new TraefikConfiguration.Observability(false));
    assertThat(http.routers().get("quiet__https").observability())
        .isEqualTo(new TraefikConfiguration.Observability(false));
  }

  @Test
  void aServiceThatRequiresAClientCertificateAnswersOnlyOnHttps() {
    Service secret = service("a", "secret", List.of("login"), clientCertificateProfile(), true);
    Workstation workstation = new Workstation("a", "a.lvh.me");

    var withoutHttps = RouteBuilder.build(workstation, List.of(secret), false).http();
    var withHttps = RouteBuilder.build(workstation, List.of(secret), true).http();

    assertThat(withoutHttps.routers()).isEmpty();
    assertThat(withoutHttps.services()).isEmpty();
    assertThat(withHttps.routers()).containsOnlyKeys("secret__https");
    var router = withHttps.routers().get("secret__https");
    assertThat(router.tls()).containsEntry("options", "yard-client-certificate@file");
    // The certificate rule is a TLS option, so it gives Traefik no middleware.
    assertThat(router.middlewares()).isEmpty();
    assertThat(withHttps.middlewares()).isEmpty();
  }

  @Test
  void aProfileThatTheHomeWorkstationDoesNotHoldIsStillListedSoTraefikRefusesTheRoute() {
    Service web = service("a", "web", List.of("gone"), Map.of(), true);

    var http = RouteBuilder.build(new Workstation("a", "a.lvh.me"), List.of(web), false).http();

    assertThat(http.routers().get("web").middlewares()).containsExactly("a_gone");
    assertThat(http.middlewares()).isEmpty();
  }

  @Test
  void aChainNamesItsMembersAsTheyAreInTraefik() {
    Map<String, Map<String, Object>> middlewares =
        Map.of(
            "team", Map.of("chain", Map.of("middlewares", List.of("login"))),
            "login", Map.of("basicAuth", Map.of("users", List.of("alice:hash"))));
    Service web =
        service(
            "a",
            "web",
            List.of("team"),
            Map.of("team", new RouteProfileDefinition("team", middlewares)),
            true);

    var http = RouteBuilder.build(new Workstation("a", "a.lvh.me"), List.of(web), false).http();

    assertThat(http.routers().get("web").middlewares()).containsExactly("a_team");
    assertThat(http.middlewares()).containsOnlyKeys("a_team", "a_login");
    assertThat(http.middlewares().get("a_team"))
        .isEqualTo(Map.of("chain", Map.of("middlewares", List.of("a_login"))));
  }

  private static Service serviceWithProfile(
      String workstation, String name, String profile, String user) {
    Map<String, Map<String, Object>> middlewares =
        Map.of(profile, Map.of("basicAuth", Map.of("users", List.of(user + ":hash"))));
    return service(
        workstation,
        name,
        List.of(profile),
        Map.of(profile, new RouteProfileDefinition(profile, middlewares)),
        true);
  }

  /**
   * Its Route Profile is the reserved Client Certificate option, which is no Traefik middleware.
   */
  private static Map<String, RouteProfileDefinition> clientCertificateProfile() {
    return Map.of(
        "login",
        new RouteProfileDefinition(
            "login", Map.of("login", Map.of("clientCertificate", Map.of()))));
  }

  private static Service service(
      String workstation,
      String name,
      List<String> profiles,
      Map<String, RouteProfileDefinition> definitions,
      boolean accessLog) {
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
    return new Service(
        workstation,
        name,
        ServiceState.HEALTHY,
        true,
        false,
        new Publication(Scope.YARD, profiles, accessLog),
        name + "." + workstation + ".lvh.me",
        List.of(instance),
        List.of(),
        List.of(),
        List.of(),
        true,
        profiles,
        definitions,
        new Routing(null, List.of(instance), List.of(), true));
  }
}
