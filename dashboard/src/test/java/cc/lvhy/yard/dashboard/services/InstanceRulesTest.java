package cc.lvhy.yard.dashboard.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The input rules for names, ports, checks and Static Instances. They need no Consul. */
class InstanceRulesTest {

  private static final Supplier<String> DEFAULT_HOST = () -> "10.20.0.1";

  @Test
  void serviceNameThatIsNotADnsLabelIsRefused() {
    assertThatThrownBy(() -> InstanceRules.checkName("my_app"))
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessageContaining("my_app")
        .hasMessageContaining("not a single DNS label")
        .extracting("field")
        .isEqualTo("name");
  }

  @ParameterizedTest
  @ValueSource(strings = {"a", "web", "my-app-2", "1", "a-b"})
  void aDnsLabelIsAServiceName(String name) {
    assertThatCode(() -> InstanceRules.checkName(name)).doesNotThrowAnyException();
  }

  @Test
  void aServiceNameCanBe63CharactersButNot64() {
    assertThatCode(() -> InstanceRules.checkName("a".repeat(63))).doesNotThrowAnyException();
    assertRefused(() -> InstanceRules.checkName("a".repeat(64)), "name", "not a single DNS label");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "A", "Web", "-a", "a-", "a.b", "a b", "a_b"})
  void aNameThatIsNotADnsLabelIsRefused(String name) {
    assertRefused(() -> InstanceRules.checkName(name), "name", "not a single DNS label");
  }

  @Test
  void theNameConsulIsRefusedAlthoughItIsADnsLabel() {
    assertRefused(
        () -> InstanceRules.checkName("consul"),
        "name",
        "consul is Consul's own entry in its catalog");
  }

  @Test
  void theMessageNamesTheKindOfName() {
    assertRefused(
        () -> InstanceRules.checkName("Alias", "Bad_Name"),
        "name",
        "The Alias name Bad_Name is not a single DNS label");
  }

  @Test
  void portIsRequired() {
    assertThatThrownBy(() -> InstanceRules.checkPort(null))
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessage("A Static Instance needs a port.")
        .extracting("field")
        .isEqualTo("port");
    assertThatThrownBy(() -> InstanceRules.checkPort("containerPort", "A Binding", null))
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessage("A Binding needs a port.")
        .extracting("field")
        .isEqualTo("containerPort");
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 80, 65535})
  void aPortFrom1To65535IsATcpPort(int port) {
    assertThatCode(() -> InstanceRules.checkPort(port)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 65536, 99999})
  void aPortOutsideThatRangeIsRefused(int port) {
    assertThatThrownBy(() -> InstanceRules.checkPort(port))
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessage("The port " + port + " is not a TCP port: use 1 to 65535.")
        .extracting("field")
        .isEqualTo("port");
  }

  @Test
  void aPortIsParsedWithoutItsSpaces() {
    assertThat(InstanceRules.parsePort(" 80 ")).isEqualTo(80);
    assertThat(InstanceRules.parsePort("65535")).isEqualTo(65535);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void noPortTextIsNoPort(String text) {
    assertThat(InstanceRules.parsePort(text)).isNull();
  }

  @Test
  void noPortAtAllIsNoPort() {
    assertThat(InstanceRules.parsePort(null)).isNull();
  }

  @Test
  void portTextThatIsNotANumberIsRefusedAtTheNamedField() {
    assertRefused(() -> InstanceRules.parsePort("abc"), "port", "The port abc is not a TCP port");
    assertRefused(
        () -> InstanceRules.parsePort("containerPort", "8o"),
        "containerPort",
        "The port 8o is not a TCP port");
  }

  @Test
  void tagsAreSeparatedByCommasAndLoseTheirSpaces() {
    assertThat(InstanceRules.parseTags(" blue , green,, ,red"))
        .containsExactly("blue", "green", "red");
    assertThat(InstanceRules.parseTags("")).isEmpty();
    assertThat(InstanceRules.parseTags(null)).isEmpty();
  }

  @Test
  void metaIsOneKeyValueOnEachLine() {
    assertThat(InstanceRules.parseMeta(" owner = me \r\n\n  team=a=b\nempty=\n"))
        .containsExactly(
            Map.entry("owner", "me"), Map.entry("team", "a=b"), Map.entry("empty", ""));
    assertThat(InstanceRules.parseMeta(null)).isEmpty();
    assertThat(InstanceRules.parseMeta("")).isEmpty();
  }

  @Test
  void aMetaLineMayStartWithTheEqualsSign() {
    assertThat(InstanceRules.parseMeta("=v")).containsExactly(Map.entry("", "v"));
  }

  @Test
  void aMetaLineWithoutAnEqualsSignIsRefused() {
    assertRefused(
        () -> InstanceRules.parseMeta("owner=me\n  novalue  "),
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
    assertThat(InstanceRules.check(new HealthCheck("tcp", "  ", null, null)))
        .isEqualTo(new HealthCheck("tcp", null, "10s", "2s"));
  }

  @Test
  void noCheckAtAllIsATcpCheckWithTheDefaults() {
    var tcp = new HealthCheck("tcp", null, "10s", "2s");
    assertThat(InstanceRules.check(null)).isEqualTo(tcp);
    assertThat(InstanceRules.check(new HealthCheck(null, null, null, null))).isEqualTo(tcp);
  }

  @Test
  void aPathMakesTheCheckHttp() {
    assertThat(InstanceRules.check(new HealthCheck(null, "/health", null, null)))
        .isEqualTo(new HealthCheck("http", "/health", "10s", "2s"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  "})
  void anHttpCheckWithNoPathGetsTheRoot(String path) {
    assertThat(InstanceRules.check(new HealthCheck("http", path, null, null)))
        .isEqualTo(new HealthCheck("http", "/", "10s", "2s"));
    assertThat(InstanceRules.check(new HealthCheck("http", null, null, null)).path())
        .isEqualTo("/");
  }

  @ParameterizedTest
  @ValueSource(strings = {"health", "/a b", "/a#b"})
  void anHttpPathThatCannotBeGotIsRefused(String path) {
    assertRefused(new HealthCheck("http", path, null, null), "path", "is not one an HTTP check");
  }

  @Test
  void anHttpPathMayHaveAQuery() {
    assertThat(InstanceRules.check(new HealthCheck("http", "/up?a=1&b=2", null, null)).path())
        .isEqualTo("/up?a=1&b=2");
  }

  @Test
  void durationsAreGivenBackInWholeSecondsOrMilliseconds() {
    assertThat(InstanceRules.check(new HealthCheck(null, null, "1s", "500ms")))
        .isEqualTo(new HealthCheck("tcp", null, "1s", "500ms"));
    assertThat(InstanceRules.check(new HealthCheck(null, null, "1000ms", "1500ms")))
        .isEqualTo(new HealthCheck("tcp", null, "1s", "1500ms"));
    assertThat(InstanceRules.check(new HealthCheck(null, null, " 5m ", "1h")))
        .isEqualTo(new HealthCheck("tcp", null, "300s", "3600s"));
    assertThat(InstanceRules.check(new HealthCheck(null, null, "30s", "2m")))
        .isEqualTo(new HealthCheck("tcp", null, "30s", "120s"));
  }

  // ---- a request

  @Test
  void aRequestGetsTheAgentAddressAndTheDefaultCheck() {
    var checked =
        InstanceRules.checked(new StaticInstanceRequest(null, 80, null, null, null), DEFAULT_HOST);

    assertThat(checked.host()).isEqualTo("10.20.0.1");
    assertThat(checked.port()).isEqualTo(80);
    assertThat(checked.check()).isEqualTo(new HealthCheck("tcp", null, "10s", "2s"));
    assertThat(checked.tags()).isEmpty();
    assertThat(checked.meta()).isEmpty();
  }

  @Test
  void aBlankHostIsTheAgentAddressAndAnotherHostLosesItsSpaces() {
    assertThat(InstanceRules.checked(request("   ", 80), DEFAULT_HOST).host())
        .isEqualTo("10.20.0.1");
    assertThat(InstanceRules.checked(request("  db.example.com ", 80), DEFAULT_HOST).host())
        .isEqualTo("db.example.com");
  }

  @ParameterizedTest
  @ValueSource(strings = {"10.0.0.1", "db-1.example.com", "::1", "fe80::1"})
  void anIpAddressOrAHostnameIsAHost(String host) {
    assertThat(InstanceRules.checked(request(host, 80), DEFAULT_HOST).host()).isEqualTo(host);
  }

  @ParameterizedTest
  @ValueSource(strings = {"bad host", "a/b", "a?b", "50%", "a_b"})
  void aHostThatWouldBreakAUrlIsRefused(String host) {
    assertRefused(
        () -> InstanceRules.checked(request(host, 80), DEFAULT_HOST), "host", "The host " + host);
  }

  @Test
  void aRequestNeedsAValidPort() {
    assertRefused(
        () -> InstanceRules.checked(request("h", null), DEFAULT_HOST), "port", "needs a port");
    assertRefused(
        () -> InstanceRules.checked(request("h", 0), DEFAULT_HOST), "port", "not a TCP port");
  }

  @Test
  void aTagIsOneWordOf1To255Characters() {
    assertThat(
            InstanceRules.checked(
                    new StaticInstanceRequest(
                        "h", 80, null, List.of("blue", "x".repeat(255)), null),
                    DEFAULT_HOST)
                .tags())
        .containsExactly("blue", "x".repeat(255));
    for (String tag : List.of("two words", "a,b", "x".repeat(256), "")) {
      var request = new StaticInstanceRequest("h", 80, null, List.of(tag), null);
      assertRefused(() -> InstanceRules.checked(request, DEFAULT_HOST), "tags", "is not one word");
    }
  }

  @Test
  void anInstanceHasAtMost60MetaKeys() {
    assertThatCode(() -> InstanceRules.checked(withMeta(metaKeys(60)), DEFAULT_HOST))
        .doesNotThrowAnyException();
    assertRefused(
        () -> InstanceRules.checked(withMeta(metaKeys(61)), DEFAULT_HOST), "meta", "at most 60");
  }

  @Test
  void aMetaKeyIsOneConsulAllows() {
    assertRefused(
        () -> InstanceRules.checked(withMeta(Map.of("bad key", "v")), DEFAULT_HOST),
        "meta",
        "The meta key bad key is not one Consul allows");
    assertRefused(
        () -> InstanceRules.checked(withMeta(Map.of("k".repeat(129), "v")), DEFAULT_HOST),
        "meta",
        "is not one Consul allows");
    assertThatCode(
            () -> InstanceRules.checked(withMeta(Map.of("k".repeat(128), "v")), DEFAULT_HOST))
        .doesNotThrowAnyException();
  }

  @Test
  void aMetaKeyThatLvhYardOrConsulKeepsIsRefused() {
    assertRefused(
        () -> InstanceRules.checked(withMeta(Map.of("lvh-yard-x", "v")), DEFAULT_HOST),
        "meta",
        "kept for LVH Yard");
    assertRefused(
        () -> InstanceRules.checked(withMeta(Map.of("consul-x", "v")), DEFAULT_HOST),
        "meta",
        "kept for Consul");
  }

  @Test
  void aMetaValueHasAtMost512Characters() {
    assertThatCode(
            () -> InstanceRules.checked(withMeta(Map.of("k", "v".repeat(512))), DEFAULT_HOST))
        .doesNotThrowAnyException();
    assertRefused(
        () -> InstanceRules.checked(withMeta(Map.of("k", "v".repeat(513))), DEFAULT_HOST),
        "meta",
        "The meta value of k is too long");
  }

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

  private static void assertRefused(HealthCheck check, String field, String reason) {
    assertRefused(() -> InstanceRules.check(check), field, reason);
  }

  private static void assertRefused(ThrowingCallable call, String field, String reason) {
    assertThatThrownBy(call)
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessageContaining(reason)
        .extracting("field")
        .isEqualTo(field);
  }
}
