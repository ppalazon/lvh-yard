package cc.lvhy.yard.dashboard.services;

import static org.assertj.core.api.Assertions.assertThat;

import cc.lvhy.yard.dashboard.OutOfRange;
import cc.lvhy.yard.dashboard.consul.Consul.AgentService;
import cc.lvhy.yard.dashboard.consul.Consul.CheckStatus;
import cc.lvhy.yard.dashboard.consul.Consul.HealthEntry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** What an Instance is made of, from what Consul says about a registration. */
class InstanceMappingTest {

  private static final OutOfRange OUT_OF_RANGE =
      new OutOfRange(List.of("127.0.0.0/8", "172.17.0.0/16"));

  @ParameterizedTest
  @CsvSource({
    "10s,PT10S",
    "1m30s,PT1M30S",
    "1.5s,PT1.5S",
    "500ms,PT0.5S",
    "2h,PT2H",
    "250us,PT0.00025S",
    "250µs,PT0.00025S",
    "7ns,PT0.000000007S",
    "1h1m1s,PT1H1M1S",
    "abc,PT0S",
    "'',PT0S"
  })
  void aGoDurationIsReadInEachUnit(String text, String expected) {
    assertThat(InstanceMapping.goDuration(text)).isEqualTo(Duration.parse(expected));
  }

  @Test
  void noDurationIsZero() {
    assertThat(InstanceMapping.goDuration(null)).isEqualTo(Duration.ZERO);
  }

  @Test
  void aStaticInstanceHasItsCheckItsHttpPathAndItsHealth() {
    HealthEntry entry =
        entry(
            service(
                "web:1",
                "10.0.0.2",
                80,
                List.of("blue"),
                Map.of(
                    "owner", "me",
                    "lvh-yard-instance", "static",
                    "lvh-yard-http-path", "/up")),
            check("Serf Health Status", "passing", "alive", "", "", null, null),
            check("HTTP GET /up", "warning", null, "web:1", "http", "1m30s", "500ms"));

    Instance instance = InstanceMapping.of(entry, false, OUT_OF_RANGE);

    assertThat(instance.id()).isEqualTo("web:1");
    assertThat(instance.host()).isEqualTo("10.0.0.2");
    assertThat(instance.port()).isEqualTo(80);
    assertThat(instance.health()).isEqualTo(Health.WARNING);
    assertThat(instance.tags()).containsExactly("blue");
    assertThat(instance.meta()).containsOnly(Map.entry("owner", "me"));
    assertThat(instance.check()).isEqualTo(new HealthCheck("http", "/up", "90s", "500ms"));
    assertThat(instance.checks())
        .containsExactly(
            new CheckResult("Serf Health Status", Health.PASSING, "alive"),
            new CheckResult("HTTP GET /up", Health.WARNING, ""));
    assertThat(instance.staticInstance()).isTrue();
    assertThat(instance.bound()).isFalse();
    assertThat(instance.foreign()).isFalse();
  }

  @Test
  void theWorstCheckGivesTheHealthAndAnUnknownStatusIsCritical() {
    HealthEntry entry =
        entry(
            service("web:1", "10.0.0.2", 80, List.of(), Map.of()),
            check("a", "passing", "", "", "", null, null),
            check("b", "maintenance", "", "web:1", "tcp", "10s", "2s"));

    assertThat(InstanceMapping.of(entry, false, OUT_OF_RANGE).health()).isEqualTo(Health.CRITICAL);
  }

  @Test
  void aRegistrationWithNoChecksIsPassingAndHasNoCheck() {
    HealthEntry entry = entry(service("web:1", "10.0.0.2", 80, List.of(), Map.of()));

    Instance instance = InstanceMapping.of(entry, false, OUT_OF_RANGE);

    assertThat(instance.health()).isEqualTo(Health.PASSING);
    assertThat(instance.check()).isNull();
  }

  @Test
  void aCheckOfAnotherRegistrationIsNotThisInstancesCheck() {
    HealthEntry entry =
        entry(
            service("web:1", "10.0.0.2", 80, List.of(), Map.of()),
            check("node", "passing", "", "other", "tcp", "10s", "2s"));

    assertThat(InstanceMapping.of(entry, false, OUT_OF_RANGE).check()).isNull();
  }

  @Test
  void aBoundInstanceNamesItsBindingContainerAndPorts() {
    HealthEntry entry =
        entry(
            service(
                "web:app:80",
                "10.0.0.2",
                8080,
                List.of(),
                Map.of(
                    "lvh-yard-instance", "bound",
                    "lvh-yard-container-binding", "app:80",
                    "lvh-yard-container", "app",
                    "lvh-yard-container-port", "80")));

    Instance instance = InstanceMapping.of(entry, false, OUT_OF_RANGE);

    assertThat(instance.bound()).isTrue();
    assertThat(instance.containerBinding()).isEqualTo("app:80");
    assertThat(instance.container()).isEqualTo("app");
    assertThat(instance.containerPort()).isEqualTo(80);
    assertThat(instance.hostPort()).isEqualTo(8080);
    assertThat(instance.unpublishedContainerPort()).isFalse();
    assertThat(instance.foreign()).isFalse();
    assertThat(instance.meta()).isEmpty();
  }

  @Test
  void aBoundInstanceOnAContainerPortNotPublishedHasNoHostPort() {
    HealthEntry entry =
        entry(
            service(
                "web:app:80",
                "172.17.0.2",
                80,
                List.of(),
                Map.of(
                    "lvh-yard-instance", "bound",
                    "lvh-yard-container-binding", "app:80",
                    "lvh-yard-container", "app",
                    "lvh-yard-container-port", "80",
                    "lvh-yard-unpublished-container-port", "true")));

    Instance instance = InstanceMapping.of(entry, false, OUT_OF_RANGE);

    assertThat(instance.unpublishedContainerPort()).isTrue();
    assertThat(instance.hostPort()).isNull();
  }

  @Test
  void aRegistrationThatLvhYardDidNotMakeIsForeign() {
    HealthEntry entry = entry(service("tool:1", "10.0.0.2", 80, List.of(), Map.of("k", "v")));

    Instance instance = InstanceMapping.of(entry, false, OUT_OF_RANGE);

    assertThat(instance.foreign()).isTrue();
    assertThat(instance.staticInstance()).isFalse();
    assertThat(instance.meta()).containsOnly(Map.entry("k", "v"));
  }

  @Test
  void anAddressInRangeIsOutOfRangeOnlyForARemoteService() {
    HealthEntry loopback = entry(service("a", "127.0.0.1", 80, List.of(), Map.of()));
    HealthEntry bridge = entry(service("b", "172.17.0.5", 80, List.of(), Map.of()));
    HealthEntry lan = entry(service("c", "192.168.1.5", 80, List.of(), Map.of()));

    assertThat(InstanceMapping.of(loopback, true, OUT_OF_RANGE).outOfRange()).isTrue();
    assertThat(InstanceMapping.of(bridge, true, OUT_OF_RANGE).outOfRange()).isTrue();
    assertThat(InstanceMapping.of(lan, true, OUT_OF_RANGE).outOfRange()).isFalse();
    assertThat(InstanceMapping.of(loopback, false, OUT_OF_RANGE).outOfRange()).isFalse();
  }

  private static AgentService service(
      String id, String address, int port, List<String> tags, Map<String, String> meta) {
    return new AgentService(id, "web", address, port, tags, meta);
  }

  private static CheckStatus check(
      String name,
      String status,
      String output,
      String serviceId,
      String type,
      String interval,
      String timeout) {
    return new CheckStatus(name, status, output, serviceId, type, interval, timeout);
  }

  private static HealthEntry entry(AgentService service, CheckStatus... checks) {
    return new HealthEntry(service, List.of(checks));
  }
}
