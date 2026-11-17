package cc.lvhy.yard.dashboard.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import cc.lvhy.yard.dashboard.profiles.RouteProfileDefinition;
import cc.lvhy.yard.dashboard.web.RouteProfileSummary.Middleware;
import cc.lvhy.yard.dashboard.web.RouteProfileSummary.Option;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RouteProfileSummaryTest {

  private static RouteProfileSummary summary(
      String profile, Map<String, Map<String, Object>> middlewares) {
    return RouteProfileSummary.of(
            List.of(profile), Map.of(profile, new RouteProfileDefinition(profile, middlewares)))
        .getFirst();
  }

  @Test
  void showsHeaderValuesAndOtherOptions() {
    RouteProfileSummary summary =
        summary(
            "headers",
            Map.of(
                "headers",
                Map.of(
                    "headers",
                    Map.of(
                        "customRequestHeaders",
                        Map.of("X-Forwarded-Proto", "https"),
                        "frameDeny",
                        true))));

    assertThat(summary.middlewares().getFirst().options())
        .containsExactlyInAnyOrder(
            new Option("customRequestHeaders.X-Forwarded-Proto", "https"),
            new Option("frameDeny", "true"));
  }

  @Test
  void hidesTheUsersOfBasicAndDigestAuthButNotTheirOtherOptions() {
    RouteProfileSummary basic =
        summary(
            "login",
            Map.of(
                "login",
                Map.of(
                    "basicAuth",
                    Map.of("users", List.of("ana:$2y$05$hash"), "removeHeader", true))));
    RouteProfileSummary digest =
        summary(
            "digest",
            Map.of(
                "digest", Map.of("digestAuth", Map.of("usersFile", "/etc/users", "realm", "dev"))));

    Middleware login = basic.middlewares().getFirst();
    assertThat(login.authentication()).isTrue();
    assertThat(login.options())
        .containsExactlyInAnyOrder(new Option("users", null), new Option("removeHeader", "true"));
    assertThat(digest.middlewares().getFirst().options())
        .containsExactlyInAnyOrder(new Option("usersFile", null), new Option("realm", "dev"));
  }

  @Test
  void stripsTheCredentialsAndQueryOfAForwardAuthAddress() {
    RouteProfileSummary summary =
        summary(
            "sso",
            Map.of(
                "sso",
                Map.of(
                    "forwardAuth",
                    Map.of("address", "https://ana:secret@auth.lan:9091/check?token=abc"))));

    assertThat(summary.middlewares().getFirst().options())
        .containsExactly(new Option("address", "https://auth.lan:9091/check"));
  }

  @Test
  void hidesEveryPluginOption() {
    RouteProfileSummary summary =
        summary(
            "plug", Map.of("plug", Map.of("plugin", Map.of("demo", Map.of("apiKey", "s3cret")))));

    assertThat(summary.middlewares().getFirst().options())
        .containsExactly(new Option("demo", null));
  }

  @Test
  void spreadsAChainInOrderAndMarksTheClientCertificateAsAuthentication() {
    RouteProfileSummary summary =
        summary(
            "protected",
            Map.of(
                "protected", Map.of("chain", Map.of("middlewares", List.of("cert", "squeeze"))),
                "cert", Map.of("clientCertificate", Map.of()),
                "squeeze", Map.of("compress", Map.of())));

    assertThat(summary.middlewares())
        .extracting(Middleware::type, Middleware::authentication)
        .containsExactly(tuple("clientCertificate", true), tuple("compress", false));
  }

  @Test
  void aProfileTheHomeWorkstationDoesNotHoldIsMissing() {
    RouteProfileSummary missing = RouteProfileSummary.of(List.of("gone"), Map.of()).getFirst();

    assertThat(missing.missing()).isTrue();
    assertThat(missing.middlewares()).isEmpty();
  }

  @Test
  void aChainThatReachesItselfIsSpreadOnce() {
    RouteProfileSummary summary =
        summary(
            "loop",
            Map.of(
                "loop", Map.of("chain", Map.of("middlewares", List.of("loop", "squeeze"))),
                "squeeze", Map.of("compress", Map.of())));

    assertThat(summary.middlewares()).extracting(Middleware::type).containsExactly("compress");
  }
}
