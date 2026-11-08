package dev.palazon.lvhyard.dashboard.consul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.palazon.lvhyard.dashboard.consul.Consul.AgentService;
import dev.palazon.lvhyard.dashboard.consul.Consul.Check;
import dev.palazon.lvhyard.dashboard.consul.Consul.CheckStatus;
import dev.palazon.lvhyard.dashboard.consul.Consul.HealthEntry;
import dev.palazon.lvhyard.dashboard.consul.Consul.Registration;
import dev.palazon.lvhyard.dashboard.consul.Consul.Watched;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;

/**
 * What the dashboard needs Consul to do, run against {@link FakeConsul} and against a real Consul,
 * so that the fake cannot drift from the real one.
 */
abstract class ConsulContract {

  /** The datacenter of the Consul under test. */
  static final String DATACENTER = "ws-contract";

  abstract Consul consul();

  /** An address, {@code host:port}, where something accepts TCP connections. */
  abstract String listening();

  /** An address where nothing listens. */
  abstract String silent();

  /** An address that answers every HTTP request with {@code statusLine} and {@code body}. */
  abstract String answeringHttp(String statusLine, String body);

  @Test
  void ownDatacenterHasALeaderAndAnAliveWanMember() {
    assertThat(consul().datacenters()).contains(DATACENTER);
    assertThat(consul().leader()).endsWith(":8300");
    assertThat(consul().wanMembers())
        .anySatisfy(member -> {
          assertThat(member.datacenter()).isEqualTo(DATACENTER);
          assertThat(member.alive()).isTrue();
        });
    assertThat(consul().agentAddress()).isNotBlank();
  }

  @Test
  void registrationIsAnAgentServiceUntilItIsDeregistered() {
    String id = newName();
    consul().register(registration(id, "web", null));

    AgentService service = consul().agentService(id).orElseThrow();
    assertThat(service.name()).isEqualTo("web");
    assertThat(service.address()).isEqualTo("10.1.2.3");
    assertThat(service.port()).isEqualTo(8080);
    assertThat(service.meta()).containsEntry("owner", "test");
    assertThat(consul().agentServices()).containsKey(id).doesNotContainKey("consul");

    consul().deregister(id);

    assertThat(consul().agentService(id)).isEmpty();
  }

  @Test
  void catalogNamesConsulItselfAndEachService() {
    String name = newName();
    consul().register(registration(newName(), name, null));

    assertThat(consul().serviceNames(null)).contains("consul", name);
  }

  @Test
  void checkStartsCriticalWithItsSettingsNextToTheNodesPassingCheck() {
    String id = newName();
    String name = newName();
    consul()
        .register(
            registration(
                id, name, new Check("Probe", "http://10.1.2.3:8080/health", null, "1s", "2s")));

    HealthEntry entry = consul().health(name).getFirst();

    assertThat(entry.service().id()).isEqualTo(id);
    assertThat(entry.checks()).hasSize(2);
    CheckStatus node =
        entry.checks().stream().filter(check -> check.serviceId().isEmpty()).findFirst().get();
    assertThat(node.status()).isEqualTo("passing");
    CheckStatus probe =
        entry.checks().stream().filter(check -> check.serviceId().equals(id)).findFirst().get();
    assertThat(probe.name()).isEqualTo("Probe");
    assertThat(probe.status()).isEqualTo("critical");
    assertThat(probe.type()).isEqualTo("http");
    assertThat(probe.interval()).isEqualTo("1s");
    assertThat(probe.timeout()).isEqualTo("2s");
  }

  @Test
  void tcpCheckPassesWhereSomethingListensAndFailsElsewhere() {
    String up = newName();
    String down = newName();
    consul().register(registration(newName(), up, new Check("TCP", null, listening(), "1s", "1s")));
    consul().register(registration(newName(), down, new Check("TCP", null, silent(), "1s", "1s")));

    assertCheck(up, "passing", "Success");
    assertCheck(down, "critical", "connection refused");
  }

  @Test
  void httpCheckFollowsTheStatusCodeAndKeepsTheBody() {
    String ok = newName();
    String busy = newName();
    String failing = newName();
    consul().register(registration(newName(), ok, httpCheck(answeringHttp("200 OK", "fine"))));
    consul()
        .register(
            registration(
                newName(), busy, httpCheck(answeringHttp("429 Too Many Requests", "later"))));
    consul()
        .register(
            registration(
                newName(),
                failing,
                httpCheck(answeringHttp("503 Service Unavailable", "warming"))));

    assertCheck(ok, "passing", "200 OK");
    assertCheck(busy, "warning", "429 Too Many Requests");
    assertCheck(failing, "critical", "503 Service Unavailable");
    assertCheck(failing, "critical", "warming");
  }

  private static Check httpCheck(String address) {
    return new Check("HTTP", "http://" + address + "/health", null, "1s", "1s");
  }

  /** Waits until the check of {@code service}'s one registration has {@code status}. */
  private void assertCheck(String service, String status, String output) {
    org.awaitility.Awaitility.await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(() -> {
          CheckStatus check =
              consul().health(service).getFirst().checks().stream()
                  .filter(found -> !found.serviceId().isEmpty())
                  .findFirst()
                  .orElseThrow();
          assertThat(check.status()).isEqualTo(status);
          assertThat(check.output()).contains(output);
        });
  }

  @Test
  void registrationWithoutACheckHasOnlyItsNodesCheck() {
    String name = newName();
    consul().register(registration(newName(), name, null));

    assertThat(consul().health(name).getFirst().checks())
        .singleElement()
        .satisfies(check -> assertThat(check.serviceId()).isEmpty());
  }

  @Test
  void valuesAreReadByPrefixAndAnEmptyOneReadsEmpty() {
    String prefix = "lvh-yard/contract-" + newName() + "/";
    consul().putValue(prefix + "a", "one");
    consul().putValue(prefix + "b", "");

    assertThat(consul().values(null, prefix))
        .containsExactly(Map.entry(prefix + "a", "one"), Map.entry(prefix + "b", ""));
    assertThat(consul().values(null, prefix + "none/")).isEmpty();

    consul().deleteValue(prefix + "a");
    consul().deleteValue(prefix + "missing");

    assertThat(consul().values(null, prefix)).containsOnlyKeys(prefix + "b");
  }

  @Test
  void oneTransactionWritesEveryValueAndRefusesMoreThanItsLimit() {
    String prefix = "lvh-yard/contract-" + newName() + "/";
    Map<String, String> values = new HashMap<>();
    for (int i = 0; i < Consul.TRANSACTION_SIZE; i++) {
      values.put(prefix + i, "v" + i);
    }

    consul().putValues(values);

    assertThat(consul().values(null, prefix)).hasSize(Consul.TRANSACTION_SIZE);
    values.put(prefix + "one-more", "v");
    assertThatThrownBy(() -> consul().putValues(values))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void blockingQueryAnswersAtOnceForIndexZeroAndWhenSomethingChanged() {
    long index = consul().awaitChange(Watched.KV, 0, Duration.ofSeconds(5));
    assertThat(index).isPositive();

    consul().putValue("lvh-yard/contract-" + newName(), "changed");
    long started = System.nanoTime();
    long changed = consul().awaitChange(Watched.KV, index, Duration.ofSeconds(5));

    assertThat(changed).isGreaterThan(index);
    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
  }

  @Test
  void unknownDatacenterFailsAsAServerError() {
    assertThatThrownBy(() -> consul().health("nowhere", "web"))
        .isInstanceOf(HttpServerErrorException.class);
  }

  private static Registration registration(String id, String name, Check check) {
    return new Registration(id, name, "10.1.2.3", 8080, List.of(), Map.of("owner", "test"), check);
  }

  private static String newName() {
    return "contract-" + UUID.randomUUID().toString().substring(0, 8);
  }
}
