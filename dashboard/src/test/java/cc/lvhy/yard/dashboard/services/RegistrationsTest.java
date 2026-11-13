package cc.lvhy.yard.dashboard.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
import java.util.stream.IntStream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The rules of a Static Instance and what {@link Registrations} writes to the local agent, with
 * {@link FakeConsul} for the agent.
 */
class RegistrationsTest {

  private final FakeConsul consul = new FakeConsul("home");

  private final ServiceRecords records = new ServiceRecords(consul);

  private final Registrations registrations =
      new Registrations(
          new Workstation("home", "home.lvh.me"), consul, records, Duration.ofSeconds(3));

  // ---- the input rules, with no Consul

  @Test
  void serviceNameThatIsNotADnsLabelIsRefused() {
    assertThatThrownBy(() -> Registrations.checkName("my_app"))
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessageContaining("my_app")
        .hasMessageContaining("not a single DNS label")
        .extracting("field")
        .isEqualTo("name");
  }

  @ParameterizedTest
  @ValueSource(strings = {"a", "web", "my-app-2", "1", "a-b"})
  void aDnsLabelIsAServiceName(String name) {
    assertThatCode(() -> Registrations.checkName(name)).doesNotThrowAnyException();
  }

  @Test
  void aServiceNameCanBe63CharactersButNot64() {
    assertThatCode(() -> Registrations.checkName("a".repeat(63))).doesNotThrowAnyException();
    assertRefused(() -> Registrations.checkName("a".repeat(64)), "name", "not a single DNS label");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "A", "Web", "-a", "a-", "a.b", "a b", "a_b"})
  void aNameThatIsNotADnsLabelIsRefused(String name) {
    assertRefused(() -> Registrations.checkName(name), "name", "not a single DNS label");
  }

  @Test
  void theNameConsulIsRefusedAlthoughItIsADnsLabel() {
    assertRefused(
        () -> Registrations.checkName("consul"),
        "name",
        "consul is Consul's own entry in its catalog");
  }

  @Test
  void theMessageNamesTheKindOfName() {
    assertRefused(
        () -> Registrations.checkName("Alias", "Bad_Name"),
        "name",
        "The Alias name Bad_Name is not a single DNS label");
  }

  @Test
  void portIsRequired() {
    assertThatThrownBy(() -> Registrations.checkPort(null))
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessage("A Static Instance needs a port.")
        .extracting("field")
        .isEqualTo("port");
    assertThatThrownBy(() -> Registrations.checkPort("containerPort", "A Binding", null))
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessage("A Binding needs a port.")
        .extracting("field")
        .isEqualTo("containerPort");
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 80, 65535})
  void aPortFrom1To65535IsATcpPort(int port) {
    assertThatCode(() -> Registrations.checkPort(port)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 65536, 99999})
  void aPortOutsideThatRangeIsRefused(int port) {
    assertThatThrownBy(() -> Registrations.checkPort(port))
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessage("The port " + port + " is not a TCP port: use 1 to 65535.")
        .extracting("field")
        .isEqualTo("port");
  }

  @Test
  void aPortIsParsedWithoutItsSpaces() {
    assertThat(Registrations.parsePort(" 80 ")).isEqualTo(80);
    assertThat(Registrations.parsePort("65535")).isEqualTo(65535);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void noPortTextIsNoPort(String text) {
    assertThat(Registrations.parsePort(text)).isNull();
  }

  @Test
  void noPortAtAllIsNoPort() {
    assertThat(Registrations.parsePort(null)).isNull();
  }

  @Test
  void portTextThatIsNotANumberIsRefusedAtTheNamedField() {
    assertRefused(() -> Registrations.parsePort("abc"), "port", "The port abc is not a TCP port");
    assertRefused(
        () -> Registrations.parsePort("containerPort", "8o"),
        "containerPort",
        "The port 8o is not a TCP port");
  }

  @Test
  void tagsAreSeparatedByCommasAndLoseTheirSpaces() {
    assertThat(Registrations.parseTags(" blue , green,, ,red"))
        .containsExactly("blue", "green", "red");
    assertThat(Registrations.parseTags("")).isEmpty();
    assertThat(Registrations.parseTags(null)).isEmpty();
  }

  @Test
  void metaIsOneKeyValueOnEachLine() {
    assertThat(Registrations.parseMeta(" owner = me \r\n\n  team=a=b\nempty=\n"))
        .containsExactly(
            Map.entry("owner", "me"), Map.entry("team", "a=b"), Map.entry("empty", ""));
    assertThat(Registrations.parseMeta(null)).isEmpty();
    assertThat(Registrations.parseMeta("")).isEmpty();
  }

  @Test
  void aMetaLineMayStartWithTheEqualsSign() {
    assertThat(Registrations.parseMeta("=v")).containsExactly(Map.entry("", "v"));
  }

  @Test
  void aMetaLineWithoutAnEqualsSignIsRefused() {
    assertRefused(
        () -> Registrations.parseMeta("owner=me\n  novalue  "),
        "meta",
        "The meta line \"novalue\" is not key=value");
  }

  @Test
  void scriptCheckIsRefused() {
    assertRefused(new HealthCheck("script", null, null, null), "check", "Script checks");
  }

  @Test
  void aCheckTypeThatIsNeitherHttpNorTcpIsRefused() {
    assertRefused(new HealthCheck("grpc", null, null, null), "check", "The check grpc");
  }

  @Test
  void intervalUnderOneSecondIsRefused() {
    assertRefused(new HealthCheck(null, null, "500ms", null), "interval", "once a second");
    assertRefused(new HealthCheck(null, null, "999ms", null), "interval", "once a second");
  }

  @Test
  void timeoutThatIsNotADurationIsRefused() {
    assertRefused(new HealthCheck(null, null, null, "2"), "timeout", "not a duration");
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "10", "s", "-5s", "1.5s", "0s", "0ms", "10d", "1234567890s"})
  void aDurationNeedsAWholeNumberAboveZeroAndAUnit(String text) {
    assertRefused(new HealthCheck(null, null, null, text), "timeout", "not a duration");
    assertRefused(new HealthCheck(null, null, text, null), "interval", "not a duration");
  }

  @Test
  void tcpCheckWithAPathIsRefused() {
    assertRefused(new HealthCheck("tcp", "/health", null, null), "path", "A TCP check has no path");
  }

  @Test
  void aTcpCheckWithABlankPathIsFine() {
    assertThat(Registrations.check(new HealthCheck("tcp", "  ", null, null)))
        .isEqualTo(new HealthCheck("tcp", null, "10s", "2s"));
  }

  @Test
  void noCheckAtAllIsATcpCheckWithTheDefaults() {
    var tcp = new HealthCheck("tcp", null, "10s", "2s");
    assertThat(Registrations.check(null)).isEqualTo(tcp);
    assertThat(Registrations.check(new HealthCheck(null, null, null, null))).isEqualTo(tcp);
  }

  @Test
  void aPathMakesTheCheckHttp() {
    assertThat(Registrations.check(new HealthCheck(null, "/health", null, null)))
        .isEqualTo(new HealthCheck("http", "/health", "10s", "2s"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  "})
  void anHttpCheckWithNoPathGetsTheRoot(String path) {
    assertThat(Registrations.check(new HealthCheck("http", path, null, null)))
        .isEqualTo(new HealthCheck("http", "/", "10s", "2s"));
    assertThat(Registrations.check(new HealthCheck("http", null, null, null)).path())
        .isEqualTo("/");
  }

  @ParameterizedTest
  @ValueSource(strings = {"health", "/a b", "/a#b"})
  void anHttpPathThatCannotBeGotIsRefused(String path) {
    assertRefused(new HealthCheck("http", path, null, null), "path", "is not one an HTTP check");
  }

  @Test
  void anHttpPathMayHaveAQuery() {
    assertThat(Registrations.check(new HealthCheck("http", "/up?a=1&b=2", null, null)).path())
        .isEqualTo("/up?a=1&b=2");
  }

  @Test
  void durationsAreGivenBackInWholeSecondsOrMilliseconds() {
    assertThat(Registrations.check(new HealthCheck(null, null, "1s", "500ms")))
        .isEqualTo(new HealthCheck("tcp", null, "1s", "500ms"));
    assertThat(Registrations.check(new HealthCheck(null, null, "1000ms", "1500ms")))
        .isEqualTo(new HealthCheck("tcp", null, "1s", "1500ms"));
    assertThat(Registrations.check(new HealthCheck(null, null, " 5m ", "1h")))
        .isEqualTo(new HealthCheck("tcp", null, "300s", "3600s"));
    assertThat(Registrations.check(new HealthCheck(null, null, "30s", "2m")))
        .isEqualTo(new HealthCheck("tcp", null, "30s", "120s"));
  }

  // ---- what a request must be, with the default host of the agent

  @Test
  void aRequestGetsTheAgentAddressAndTheDefaultCheck() {
    var checked = registrations.check("web", new StaticInstanceRequest(null, 80, null, null, null));

    assertThat(checked.host()).isEqualTo(FakeConsul.LAN_ADDRESS);
    assertThat(checked.port()).isEqualTo(80);
    assertThat(checked.check()).isEqualTo(new HealthCheck("tcp", null, "10s", "2s"));
    assertThat(checked.tags()).isEmpty();
    assertThat(checked.meta()).isEmpty();
  }

  @Test
  void aBlankHostIsTheAgentAddressAndAnotherHostLosesItsSpaces() {
    assertThat(registrations.check("web", request("   ", 80)).host())
        .isEqualTo(FakeConsul.LAN_ADDRESS);
    assertThat(registrations.check("web", request("  db.example.com ", 80)).host())
        .isEqualTo("db.example.com");
  }

  @ParameterizedTest
  @ValueSource(strings = {"10.0.0.1", "db-1.example.com", "::1", "fe80::1"})
  void anIpAddressOrAHostnameIsAHost(String host) {
    assertThat(registrations.check("web", request(host, 80)).host()).isEqualTo(host);
  }

  @ParameterizedTest
  @ValueSource(strings = {"bad host", "a/b", "a?b", "50%", "a_b"})
  void aHostThatWouldBreakAUrlIsRefused(String host) {
    assertRefused(() -> registrations.check("web", request(host, 80)), "host", "The host " + host);
  }

  @Test
  void aRequestNeedsAValidPortAndServiceName() {
    assertRefused(() -> registrations.check("web", request("h", null)), "port", "needs a port");
    assertRefused(() -> registrations.check("web", request("h", 0)), "port", "not a TCP port");
    assertRefused(() -> registrations.check("Web", request("h", 80)), "name", "DNS label");
    assertRefused(() -> registrations.check("consul", request("h", 80)), "name", "consul");
  }

  @Test
  void aTagIsOneWordOf1To255Characters() {
    assertThat(
            registrations
                .check(
                    "web",
                    new StaticInstanceRequest(
                        "h", 80, null, List.of("blue", "x".repeat(255)), null))
                .tags())
        .containsExactly("blue", "x".repeat(255));
    for (String tag : List.of("two words", "a,b", "x".repeat(256), "")) {
      var request = new StaticInstanceRequest("h", 80, null, List.of(tag), null);
      assertRefused(() -> registrations.check("web", request), "tags", "is not one word");
    }
  }

  @Test
  void anInstanceHasAtMost60MetaKeys() {
    assertThatCode(() -> registrations.check("web", withMeta(metaKeys(60))))
        .doesNotThrowAnyException();
    assertRefused(() -> registrations.check("web", withMeta(metaKeys(61))), "meta", "at most 60");
  }

  @Test
  void aMetaKeyIsOneConsulAllows() {
    assertRefused(
        () -> registrations.check("web", withMeta(Map.of("bad key", "v"))),
        "meta",
        "The meta key bad key is not one Consul allows");
    assertRefused(
        () -> registrations.check("web", withMeta(Map.of("k".repeat(129), "v"))),
        "meta",
        "is not one Consul allows");
    assertThatCode(() -> registrations.check("web", withMeta(Map.of("k".repeat(128), "v"))))
        .doesNotThrowAnyException();
  }

  @Test
  void aMetaKeyThatLvhYardOrConsulKeepsIsRefused() {
    assertRefused(
        () -> registrations.check("web", withMeta(Map.of("lvh-yard-x", "v"))),
        "meta",
        "kept for LVH Yard");
    assertRefused(
        () -> registrations.check("web", withMeta(Map.of("consul-x", "v"))),
        "meta",
        "kept for Consul");
  }

  @Test
  void aMetaValueHasAtMost512Characters() {
    assertThatCode(() -> registrations.check("web", withMeta(Map.of("k", "v".repeat(512)))))
        .doesNotThrowAnyException();
    assertRefused(
        () -> registrations.check("web", withMeta(Map.of("k", "v".repeat(513)))),
        "meta",
        "The meta value of k is too long");
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

  private static StaticInstanceRequest withMeta(Map<String, String> meta) {
    return new StaticInstanceRequest("h", 80, null, null, meta);
  }

  private static Map<String, String> metaKeys(int count) {
    Map<String, String> meta = new java.util.TreeMap<>();
    IntStream.range(0, count).forEach(number -> meta.put("key" + number, "v"));
    return meta;
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

  private static void assertRefused(HealthCheck check, String field, String reason) {
    assertRefused(() -> Registrations.check(check), field, reason);
  }

  private static void assertRefused(ThrowingCallable call, String field, String reason) {
    assertThatThrownBy(call)
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessageContaining(reason)
        .extracting("field")
        .isEqualTo(field);
  }
}
