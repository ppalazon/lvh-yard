package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.palazon.lvhyard.dashboard.profiles.RouteProfileChecks;
import dev.palazon.lvhyard.dashboard.profiles.RouteProfileChecks.Checked;
import dev.palazon.lvhyard.dashboard.profiles.RouteProfileChecks.Problem;
import dev.palazon.lvhyard.dashboard.profiles.RouteProfiles;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.yaml.snakeyaml.Yaml;

/**
 * The checks that every write path makes on a set of Route Profiles, against what the test
 * Workstation's Consul KV holds. No write path takes a set yet other than {@code
 * route-profiles.yaml}, so the test calls the checks of the running dashboard.
 */
class RouteProfileChecksTest extends DashboardTest {

  private static final String STORED = "lvh-yard/route-profiles/stored-headers";

  @Autowired RouteProfileChecks checks;

  @Autowired RouteProfiles profiles;

  @AfterEach
  void removeTheStoredProfile() throws Exception {
    consulContainer.execInContainer("consul", "kv", "delete", STORED);
  }

  @Test
  void chainCanNameAProfileAlreadyInConsul() throws Exception {
    consulContainer.execInContainer(
        "consul",
        "kv",
        "put",
        STORED,
        "{\"stored-headers\": {\"headers\": {\"customRequestHeaders\": {\"X-Stored\": \"yes\"}}}}");

    Checked checked =
        check(
            """
            protected:
              chain:
                middlewares: [stored-headers]
            """);

    assertThat(checked.problems()).isEmpty();
    assertThat(checked.definitions())
        .isEqualTo(
            Map.of(
                "protected",
                Map.of(
                    "protected",
                    Map.of("chain", Map.of("middlewares", List.of("stored-headers"))),
                    "stored-headers",
                    Map.of("headers", Map.of("customRequestHeaders", Map.of("X-Stored", "yes"))))));
  }

  @Test
  void chainCanNameAProfileOfTheSameSet() {
    Checked checked =
        check(
            """
            compressed:
              compress:
            protected:
              chain:
                middlewares: [compressed]
            """);

    assertThat(checked.problems()).isEmpty();
    assertThat(checked.definitions().get("protected"))
        .isEqualTo(
            Map.of(
                "protected",
                Map.of("chain", Map.of("middlewares", List.of("compressed"))),
                "compressed",
                Map.of("compress", Map.of())));
  }

  @Test
  void eachProblemNamesItsProfileAndTheReason() {
    Checked checked =
        check(
            """
            fine:
              headers:
                customRequestHeaders:
                  X-Forwarded-Proto: https
            from-file:
              basicAuth:
                usersFile: /etc/traefik/users
            with-plugin:
              plugin:
                my-plugin:
                  option: 1
            auth-on-loopback:
              forwardAuth:
                address: http://127.0.0.1:4181/auth
            unknown-type:
              rewriteEverything: {}
            "bad@name":
              compress: {}
            to-nowhere:
              chain:
                middlewares: [fine, missing]
            """);

    assertThat(checked.problems())
        .extracting(Problem::profile)
        .containsExactly(
            "from-file",
            "with-plugin",
            "auth-on-loopback",
            "unknown-type",
            "bad@name",
            "to-nowhere");
    assertThat(reason(checked, "from-file")).contains("usersFile");
    assertThat(reason(checked, "with-plugin")).contains("plugin");
    assertThat(reason(checked, "auth-on-loopback")).contains("127.0.0.1");
    assertThat(reason(checked, "unknown-type")).contains("rewriteEverything");
    assertThat(reason(checked, "bad@name")).contains("not a valid name");
    assertThat(reason(checked, "to-nowhere")).contains("missing");
    assertThat(checked.definitions()).containsOnlyKeys("fine");
  }

  private Checked check(String yaml) {
    Map<String, Object> set = new Yaml().load(yaml);
    return checks.check(set, profiles.stored(null));
  }

  private static String reason(Checked checked, String profile) {
    return checked.problems().stream()
        .filter(problem -> problem.profile().equals(profile))
        .map(Problem::reason)
        .findFirst()
        .orElseThrow();
  }
}
