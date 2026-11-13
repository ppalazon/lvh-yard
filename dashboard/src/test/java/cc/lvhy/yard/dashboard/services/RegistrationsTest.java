package cc.lvhy.yard.dashboard.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.consul.Consul.AgentService;
import cc.lvhy.yard.dashboard.consul.Consul.CheckStatus;
import cc.lvhy.yard.dashboard.consul.Consul.Registration;
import cc.lvhy.yard.dashboard.consul.FakeConsul;
import cc.lvhy.yard.dashboard.services.Registrations.BoundInstance;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/**
 * The rules of a Static Instance and what {@link Registrations} writes to the local agent, with
 * {@link FakeConsul} for the agent.
 */
class RegistrationsTest {

  private final FakeConsul consul = new FakeConsul("home");

  private final ServiceRecords records = new ServiceRecords(consul, consul);

  private final Registrations registrations =
      new Registrations(
          new Workstation("home", "home.lvh.me"), consul, records, Duration.ofSeconds(3));

  // ---- the request check, with the agent's address

  @Test
  void aRequestGetsTheAgentAddressAndTheDefaultCheck() {
    var checked = registrations.check("web", new StaticInstanceRequest(null, 80, null, null, null));

    assertThat(checked.host()).isEqualTo(FakeConsul.LAN_ADDRESS);
    assertThat(checked.port()).isEqualTo(80);
    assertThat(checked.check()).isEqualTo(new HealthCheck("tcp", null, "10s", "2s"));
  }

  @Test
  void aRequestNeedsAValidServiceName() {
    assertRefused(() -> registrations.check("Web", request("h", 80)), "name", "DNS label");
    assertRefused(() -> registrations.check("consul", request("h", 80)), "name", "consul");
  }

  // ---- Static Instances

  @Test
  void aStaticInstanceIsRegisteredWithItsSettingsAndMarked() {
    records.put("web");
    var request =
        new StaticInstanceRequest(
            "10.0.0.5",
            8080,
            new HealthCheck("http", "/up", null, null),
            List.of("blue"),
            Map.of("owner", "me"));

    Instance instance = registrations.register("home", "web", request);

    assertThat(instance.host()).isEqualTo("10.0.0.5");
    assertThat(instance.port()).isEqualTo(8080);
    assertThat(instance.health()).isEqualTo(Health.CRITICAL);
    AgentService stored = consul.agentService(instance.id()).orElseThrow();
    assertThat(stored.name()).isEqualTo("web");
    assertThat(stored.address()).isEqualTo("10.0.0.5");
    assertThat(stored.port()).isEqualTo(8080);
    assertThat(stored.tags()).containsExactly("blue");
    assertThat(stored.meta())
        .containsOnly(
            Map.entry("owner", "me"),
            Map.entry("lvh-yard-instance", "static"),
            Map.entry("lvh-yard-http-path", "/up"));
    assertThat(records.get("web").orElseThrow().order()).containsExactly(instance.id());
    CheckStatus check = checkOf("web", instance.id());
    assertThat(check.name()).isEqualTo("HTTP GET /up");
    assertThat(check.interval()).isEqualTo("10s");
    assertThat(check.timeout()).isEqualTo("2s");
  }

  @Test
  void aTcpCheckKeepsNoPathAndTheAgentAddressIsTheDefaultHost() {
    records.put("web");

    Instance instance = registrations.register("home", "web", request(null, 9000));

    AgentService stored = consul.agentService(instance.id()).orElseThrow();
    assertThat(stored.address()).isEqualTo(FakeConsul.LAN_ADDRESS);
    assertThat(stored.meta()).containsOnly(Map.entry("lvh-yard-instance", "static"));
    assertThat(checkOf("web", instance.id()).name())
        .isEqualTo("TCP " + FakeConsul.LAN_ADDRESS + ":9000");
  }

  @Test
  void theSameHostAndPortAgainReplacesTheInstanceAndKeepsItsId() {
    records.put("web");
    Instance first =
        registrations.register(
            "home", "web", new StaticInstanceRequest("h", 80, null, List.of("blue"), null));

    Instance second =
        registrations.register(
            "home", "web", new StaticInstanceRequest("h", 80, null, List.of("green"), null));

    assertThat(second.id()).isEqualTo(first.id());
    assertThat(consul.agentServices()).hasSize(1);
    assertThat(consul.agentService(first.id()).orElseThrow().tags()).containsExactly("green");
    assertThat(records.get("web").orElseThrow().order()).containsExactly(first.id());
  }

  @Test
  void anotherHostOrPortOrServiceIsAnotherInstance() {
    records.put("web");
    records.put("api");

    String first = registrations.register("home", "web", request("h", 80)).id();
    String otherPort = registrations.register("home", "web", request("h", 81)).id();
    String otherHost = registrations.register("home", "web", request("i", 80)).id();
    String otherService = registrations.register("home", "api", request("h", 80)).id();

    assertThat(List.of(first, otherPort, otherHost, otherService)).doesNotHaveDuplicates();
    assertThat(records.get("web").orElseThrow().order())
        .containsExactly(first, otherPort, otherHost);
  }

  @Test
  void aServiceWithNoRecordCannotTakeAnInstance() {
    assertThatThrownBy(() -> registrations.register("home", "web", request("h", 80)))
        .isInstanceOf(UnknownServiceException.class);
    assertThat(consul.agentServices()).isEmpty();
  }

  @Test
  void aServiceNameThatIsNotADnsLabelTakesNoInstance() {
    assertRefused(
        () -> registrations.register("home", "Bad_Name", request("h", 80)),
        "name",
        "not a single DNS label");
    assertThat(consul.agentServices()).isEmpty();
  }

  @Test
  void anotherWorkstationsServiceCannotBeChanged() {
    records.put("web");
    String id = registrations.register("home", "web", request("h", 80)).id();

    assertThatThrownBy(() -> registrations.register("away", "web", request("h", 81)))
        .isInstanceOf(RemoteServiceException.class)
        .hasMessage("The Services of away can be changed only on away.");
    assertThatThrownBy(() -> registrations.edit("away", "web", id, request("h", 82)))
        .isInstanceOf(RemoteServiceException.class);
    assertThatThrownBy(() -> registrations.remove("away", "web", id))
        .isInstanceOf(RemoteServiceException.class);
    assertThat(consul.agentServices()).containsOnlyKeys(id);
    assertThat(consul.agentService(id).orElseThrow().port()).isEqualTo(80);
  }

  @Test
  void staticInstanceAtFindsOnlyAStaticInstanceOfThatServiceAtThatHostAndPort() {
    records.put("web");
    records.put("api");
    String id = registrations.register("home", "web", request("h", 80)).id();
    registrations.register("home", "api", request("h", 90));
    registrations.keepBoundInstances(List.of(bound("web", "app:80", "app", 80, "b", 70, true)));

    assertThat(registrations.staticInstanceAt("web", "h", 80)).contains(id);
    assertThat(registrations.staticInstanceAt("web", "h", 81)).isEmpty();
    assertThat(registrations.staticInstanceAt("web", "i", 80)).isEmpty();
    assertThat(registrations.staticInstanceAt("api", "h", 80)).isEmpty();
    assertThat(registrations.staticInstanceAt("web", "b", 70)).isEmpty();
  }

  @Test
  void anEditKeepsTheIdAndMovesTheInstance() {
    records.put("web");
    String id = registrations.register("home", "web", request("h", 80)).id();

    Instance edited =
        registrations.edit(
            "home", "web", id, new StaticInstanceRequest("i", 81, null, List.of("new"), null));

    assertThat(edited.id()).isEqualTo(id);
    AgentService stored = consul.agentService(id).orElseThrow();
    assertThat(stored.address()).isEqualTo("i");
    assertThat(stored.port()).isEqualTo(81);
    assertThat(stored.tags()).containsExactly("new");
    assertThat(consul.agentServices()).hasSize(1);
    assertThat(records.get("web").orElseThrow().order()).containsExactly(id);
  }

  @Test
  void anEditMayKeepTheHostAndPort() {
    records.put("web");
    String id = registrations.register("home", "web", request("h", 80)).id();

    registrations.edit(
        "home", "web", id, new StaticInstanceRequest("h", 80, null, List.of("again"), null));

    assertThat(consul.agentService(id).orElseThrow().tags()).containsExactly("again");
  }

  @Test
  void anEditCannotTakeTheAddressOfAnotherInstance() {
    records.put("web");
    String first = registrations.register("home", "web", request("h", 80)).id();
    registrations.register("home", "web", request("h", 90));

    assertThatThrownBy(() -> registrations.edit("home", "web", first, request("h", 90)))
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessageContaining("Another Instance of web is already at h:90")
        .extracting("field")
        .isEqualTo("port");
    assertThat(consul.agentService(first).orElseThrow().port()).isEqualTo(80);
  }

  @Test
  void onlyAStaticInstanceOfThatServiceCanBeEditedOrRemoved() {
    records.put("web");
    records.put("api");
    String web = registrations.register("home", "web", request("h", 80)).id();
    registrations.keepBoundInstances(List.of(bound("web", "app:80", "app", 80, "b", 70, true)));

    for (String[] other : new String[][] {{"api", web}, {"web", "nope"}, {"web", "web:app:80"}}) {
      assertThatThrownBy(() -> registrations.edit("home", other[0], other[1], request("h", 99)))
          .isInstanceOf(UnknownInstanceException.class);
      assertThatThrownBy(() -> registrations.remove("home", other[0], other[1]))
          .isInstanceOf(UnknownInstanceException.class);
    }
    assertThat(consul.agentServices()).containsOnlyKeys(web, "web:app:80");
  }

  @Test
  void removingAStaticInstanceDeregistersItAndTakesItOutOfTheOrder() {
    records.put("web");
    String first = registrations.register("home", "web", request("h", 80)).id();
    String second = registrations.register("home", "web", request("h", 81)).id();

    registrations.remove("home", "web", first);

    assertThat(consul.agentServices()).containsOnlyKeys(second);
    assertThat(records.get("web").orElseThrow().order()).containsExactly(second);
  }

  @Test
  void removingTheStaticInstancesOfAServiceLeavesBoundInstancesAndOtherServices() {
    records.put("web");
    records.put("api");
    registrations.register("home", "web", request("h", 80));
    registrations.register("home", "web", request("h", 81));
    String api = registrations.register("home", "api", request("h", 80)).id();
    registrations.keepBoundInstances(List.of(bound("web", "app:80", "app", 80, "b", 70, true)));

    registrations.removeStaticInstances("web");

    assertThat(consul.agentServices()).containsOnlyKeys(api, "web:app:80");
  }

  // ---- Bound Instances

  @Test
  void aBoundInstanceIsRegisteredWithTheContainerItFollows() {
    registrations.keepBoundInstances(
        List.of(bound("web", "app:80", "app", 80, "172.17.0.2", 80, true)));

    AgentService stored = consul.agentService("web:app:80").orElseThrow();
    assertThat(stored.name()).isEqualTo("web");
    assertThat(stored.address()).isEqualTo("172.17.0.2");
    assertThat(stored.port()).isEqualTo(80);
    assertThat(stored.tags()).isEmpty();
    assertThat(stored.meta())
        .containsOnly(
            Map.entry("lvh-yard-instance", "bound"),
            Map.entry("lvh-yard-container-binding", "app:80"),
            Map.entry("lvh-yard-container", "app"),
            Map.entry("lvh-yard-container-port", "80"));
    CheckStatus check = checkOf("web", "web:app:80");
    assertThat(check.name()).isEqualTo("TCP 172.17.0.2:80");
    assertThat(check.interval()).isEqualTo("3s");
  }

  @Test
  void aContainerPortNotPublishedOnTheHostIsMarked() {
    registrations.keepBoundInstances(
        List.of(bound("web", "app:80", "app", 80, "172.17.0.2", 80, false)));

    assertThat(consul.agentService("web:app:80").orElseThrow().meta())
        .containsEntry("lvh-yard-unpublished-container-port", "true");
  }

  @Test
  void aBindingOfAComposeServiceNamesTheBindingAndTheContainerInTheId() {
    registrations.keepBoundInstances(
        List.of(bound("web", "shop/web", "shop-web-1", 80, "h", 80, true)));

    assertThat(consul.agentServices()).containsOnlyKeys("web:shop/web:shop-web-1");
  }

  @Test
  void aBoundInstanceThatIsAsWantedIsLeftAloneSoItsHealthStays() {
    var wanted = List.of(bound("web", "app:80", "app", 80, "h", 80, true));
    registrations.keepBoundInstances(wanted);
    consul.setStatus("web:app:80", "passing", "ok");

    registrations.keepBoundInstances(wanted);

    assertThat(checkOf("web", "web:app:80").status()).isEqualTo("passing");
  }

  @Test
  void aBoundInstanceThatMovedIsRegisteredAgain() {
    var base = bound("web", "app:80", "app", 80, "h", 80, true);
    List<BoundInstance> moved =
        List.of(
            bound("web", "app:80", "app", 80, "other-host", 80, true),
            bound("web", "app:80", "app", 80, "h", 81, true),
            bound("web", "app:80", "app", 80, "h", 80, false));
    for (BoundInstance instance : moved) {
      registrations.keepBoundInstances(List.of(base));
      consul.setStatus("web:app:80", "passing", "ok");

      registrations.keepBoundInstances(List.of(instance));

      assertThat(checkOf("web", "web:app:80").status()).isEqualTo("critical");
      AgentService stored = consul.agentService("web:app:80").orElseThrow();
      assertThat(stored.address()).isEqualTo(instance.host());
      assertThat(stored.port()).isEqualTo(instance.port());
      assertThat(stored.meta().containsKey("lvh-yard-unpublished-container-port"))
          .isEqualTo(!instance.onHostPort());
    }
  }

  @Test
  void aBoundInstanceThatBecamePublishedOnTheHostIsRegisteredAgain() {
    registrations.keepBoundInstances(List.of(bound("web", "app:80", "app", 80, "h", 80, false)));
    consul.setStatus("web:app:80", "passing", "ok");

    registrations.keepBoundInstances(List.of(bound("web", "app:80", "app", 80, "h", 80, true)));

    assertThat(checkOf("web", "web:app:80").status()).isEqualTo("critical");
    assertThat(consul.agentService("web:app:80").orElseThrow().meta())
        .doesNotContainKey("lvh-yard-unpublished-container-port");
  }

  @Test
  void aBoundInstanceWhoseContainerPortChangedIsRegisteredAgain() {
    registrations.keepBoundInstances(List.of(bound("web", "shop/web", "c1", 80, "h", 80, true)));
    consul.setStatus("web:shop/web:c1", "passing", "ok");

    registrations.keepBoundInstances(List.of(bound("web", "shop/web", "c1", 81, "h", 80, true)));

    assertThat(checkOf("web", "web:shop/web:c1").status()).isEqualTo("critical");
    assertThat(consul.agentService("web:shop/web:c1").orElseThrow().meta())
        .containsEntry("lvh-yard-container-port", "81");
  }

  @Test
  void aRegistrationOfTheSameIdThatDriftedFromItsBindingIsRegisteredAgain() {
    BoundInstance wanted = bound("web", "shop/web", "c1", 80, "h", 80, true);
    for (Map<String, String> drifted :
        List.of(
            Map.of(
                "lvh-yard-instance", "bound",
                "lvh-yard-container-binding", "shop/other",
                "lvh-yard-container", "c1",
                "lvh-yard-container-port", "80"),
            Map.of(
                "lvh-yard-instance", "bound",
                "lvh-yard-container-binding", "shop/web",
                "lvh-yard-container", "c2",
                "lvh-yard-container-port", "80"))) {
      consul.register(
          new Registration("web:shop/web:c1", "web", "h", 80, List.of(), drifted, null));
      consul.setStatus("web:shop/web:c1", "passing", "ok");

      registrations.keepBoundInstances(List.of(wanted));

      assertThat(consul.agentService("web:shop/web:c1").orElseThrow().meta())
          .containsEntry("lvh-yard-container-binding", "shop/web")
          .containsEntry("lvh-yard-container", "c1");
    }
  }

  @Test
  void everyBoundInstanceThatIsNotWantedIsRemovedAndStaticInstancesStay() {
    records.put("web");
    String staticId = registrations.register("home", "web", request("h", 80)).id();
    registrations.keepBoundInstances(
        List.of(
            bound("web", "app:80", "app", 80, "b", 70, true),
            bound("web", "db:5432", "db", 5432, "b", 71, true)));

    registrations.keepBoundInstances(List.of(bound("web", "db:5432", "db", 5432, "b", 71, true)));

    assertThat(consul.agentServices()).containsOnlyKeys(staticId, "web:db:5432");
    registrations.keepBoundInstances(List.of());
    assertThat(consul.agentServices()).containsOnlyKeys(staticId);
  }

  @Test
  void removingTheBoundInstancesOfOneBindingKeepsTheOthers() {
    records.put("web");
    String staticId = registrations.register("home", "web", request("b", 70)).id();
    registrations.keepBoundInstances(
        List.of(
            bound("web", "app:80", "app", 80, "b", 70, true),
            bound("web", "db:5432", "db", 5432, "b", 71, true),
            bound("api", "app:80", "app", 80, "b", 72, true)));

    registrations.removeBoundInstances("web", "app:80");

    assertThat(consul.agentServices()).containsOnlyKeys(staticId, "web:db:5432", "api:app:80");
  }

  // ---- helpers

  private static StaticInstanceRequest request(String host, Integer port) {
    return new StaticInstanceRequest(host, port, null, null, null);
  }

  private static BoundInstance bound(
      String service,
      String binding,
      String container,
      int containerPort,
      String host,
      int port,
      boolean onHostPort) {
    return new BoundInstance(service, binding, container, containerPort, host, port, onHostPort);
  }

  /** The Health check of registration {@code id}, after the check of its node. */
  private CheckStatus checkOf(String service, String id) {
    List<CheckStatus> checks = new ArrayList<>();
    consul.health(service).stream()
        .filter(entry -> entry.service().id().equals(id))
        .forEach(entry -> checks.addAll(entry.checks()));
    return checks.get(1);
  }

  private static void assertRefused(ThrowingCallable call, String field, String reason) {
    assertThatThrownBy(call)
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessageContaining(reason)
        .extracting("field")
        .isEqualTo(field);
  }
}
