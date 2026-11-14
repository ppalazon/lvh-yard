package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import cc.lvhy.yard.dashboard.consul.Consul.Registration;
import cc.lvhy.yard.dashboard.docker.Docker.Container;
import cc.lvhy.yard.dashboard.docker.FakeDocker;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

/** Windows refuses IPv6 targets without changing the Instance or Reachability payloads. */
@TestPropertySource(properties = "YARD_IPV4_ONLY=true")
class IPv4OnlyComponentTest extends DashboardComponentTest {

  private static final String UNSUPPORTED = "Windows runtime supports IPv4 targets only";

  @ParameterizedTest
  @ValueSource(strings = {"::1", "2001:db8::5", "::ffff:192.0.2.1"})
  void ipv6StaticInstancesAreRefusedBeforeRegistration(String host) {
    String web = newServiceName("web");

    var response = post(instancesPath(web), Map.of("host", host, "port", 8080));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("field").asString()).isEqualTo("host");
    assertThat(response.getBody().get("detail").asString()).contains(UNSUPPORTED, "IPv4 address");
    assertThat(get(servicePath(web)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(consul.agentServices().values()).noneMatch(instance -> instance.name().equals(web));
  }

  @Test
  void anIpv6EditLeavesTheOriginalInstanceUnchanged() {
    String web = newServiceName("web");
    String id = register(web, Map.of("host", "127.0.0.1", "port", 8080)).get("id").asString();

    var response = put(instancesPath(web) + "/" + id, Map.of("host", "::1", "port", 8081));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("field").asString()).isEqualTo("host");
    assertThat(response.getBody().get("detail").asString()).contains(UNSUPPORTED);
    assertThat(consul.agentService(id).orElseThrow().address()).isEqualTo("127.0.0.1");
    assertThat(consul.agentService(id).orElseThrow().port()).isEqualTo(8080);
  }

  @ParameterizedTest
  @CsvSource({"add,false", "add,true", "update,false", "update,true"})
  void anIpv6BackupIsRefusedWithoutWritingAnything(String mode, boolean dryRun) {
    String web = newServiceName("web");
    String backup =
        """
        version: 2
        services:
          %s:
            sources:
              - static-instance:
                  host: '::1'
                  port: 8080
        """
            .formatted(web);

    var response =
        postYaml(
            BackupExportComponentTest.BACKUP_PATH + "?mode=" + mode + "&dry-run=" + dryRun, backup);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    JsonNode problem = response.getBody().get("problems").get(0);
    assertThat(problem.get("place").asString())
        .isEqualTo("services." + web + ".sources[0].static-instance.host");
    assertThat(problem.get("reason").asString()).contains(UNSUPPORTED);
    assertThat(get(servicePath(web)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @ParameterizedTest
  @CsvSource({"::1,false", "2001:db8::5,false", "2001:db8::5,true"})
  void existingIpv6InstancesFailReachabilityAndGetNoRoute(String host, boolean managed) {
    String web = newServiceName("web");
    create(web);
    consul.register(
        new Registration(
            web,
            web,
            host,
            8080,
            List.of(),
            managed ? Map.of("lvh-yard-instance", "static") : Map.of(),
            null));
    consul.setStatus(web, "passing", "A foreign check passed.");
    publish(web);

    JsonNode result = get(servicePath(web) + "/reachability").getBody();

    assertThat(result.get("passed").asBoolean()).isFalse();
    JsonNode instance = result.get("instances").get(0);
    assertThat(instance.get("reached").asBoolean()).isFalse();
    assertThat(instance.path("routed").asBoolean()).isFalse();
    assertThat(instance.get("error").asString()).contains(UNSUPPORTED);
    assertThat(result.get("routes")).isEmpty();
    assertThat(result.get("noRoute").asString()).contains(UNSUPPORTED);
    assertThat(get(servicePath(web)).getBody().has("routing")).isFalse();
    assertThat(
            TraefikConfigurationComponentTest.routedHostnames(
                get("/traefik/configuration").getBody().get("http")))
        .doesNotContain(web + ".lvh.test");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void aPassingIpv6SourceCannotHideAnIpv4SourceFromTheRoute(boolean priority) {
    String web = newServiceName("web");
    create(web);
    consul.register(new Registration(web, web, "2001:db8::5", 8080, List.of(), Map.of(), null));
    consul.setStatus(web, "passing", "A foreign check passed.");
    listenOnLanAddress(8081);
    String ipv4 = register(web, Map.of("port", 8081)).get("id").asString();
    put(instancePriorityPath(web), Map.of("on", priority, "order", List.of(web, ipv4)));
    publish(web);

    JsonNode service = get(servicePath(web)).getBody();

    assertThat(service.get("routing").get("instances").valueStream().map(JsonNode::asString))
        .containsExactly(ipv4);
    assertThat(service.get("sources").get(0).get("instances").get(0).asString()).isEqualTo(web);
    assertThat(
            TraefikConfigurationComponentTest.serverUrlsAt(
                get("/traefik/configuration").getBody().get("http"), web + ".lvh.test"))
        .containsExactly("http://" + lanAddress() + ":8081");
  }

  @Test
  void remoteIpv6AndAnIpv6OnlyAliasCannotPassOrGetARoute() {
    String target = newServiceName("remote");
    remote.registerInConsul(target, target, "2001:db8::5", 8080);
    remote.publish(target, "yard");
    String web = newServiceName("web");
    create(web);
    post(
        AliasesComponentTest.aliasesPath(web),
        Map.of("workstation", RemoteWorkstation.NAME, "service", target));
    publish(web);

    JsonNode remoteResult =
        get("/api/services/" + RemoteWorkstation.NAME + "/" + target + "/reachability").getBody();
    assertThat(remoteResult.get("passed").asBoolean()).isFalse();
    assertThat(remoteResult.get("instances").get(0).get("error").asString()).contains(UNSUPPORTED);
    assertThat(remoteResult.get("routes")).isEmpty();
    JsonNode aliasResult = get(servicePath(web) + "/reachability").getBody();
    assertThat(aliasResult.get("passed").asBoolean()).isFalse();
    assertThat(aliasResult.get("routes")).isEmpty();
    assertThat(aliasResult.get("noRoute").asString()).contains(UNSUPPORTED);
    assertThat(
            TraefikConfigurationComponentTest.routedHostnames(
                get("/traefik/configuration").getBody().get("http")))
        .doesNotContain(web + ".lvh.test", target + "." + RemoteWorkstation.NAME + ".lvh.test");
  }

  @Test
  void aHostnameWithIpv4StillPassesDirectReachability() throws IOException {
    try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.ofLiteral("127.0.0.1"))) {
      String web = newServiceName("web");
      register(web, Map.of("host", "localhost", "port", listener.getLocalPort()));

      JsonNode result = get(servicePath(web) + "/reachability").getBody();

      assertThat(result.get("passed").asBoolean()).isTrue();
      assertThat(result.get("instances").get(0).get("reached").asBoolean()).isTrue();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"::", "::1", "2001:db8::5"})
  void anIpv6OnlyPublishedPortGivesNoManagedInstance(String host) {
    String web = newServiceName("web");
    String name = newContainerName();
    start(FakeDocker.container(name).publishedOn(host, 80));

    bind(web, name, 80);

    JsonNode service = get(servicePath(web)).getBody();
    assertThat(service.get("containerBindings")).hasSize(1);
    assertThat(service.get("instances")).isEmpty();
    assertThat(consul.agentServices().values()).noneMatch(instance -> instance.name().equals(web));
  }

  @Test
  void anIpv4MappingWinsOverAnIpv6Wildcard() {
    String web = newServiceName("web");
    String name = newContainerName();
    Container container =
        start(FakeDocker.container(name).publishedOn("::", 80).alsoPublishedOn("127.0.0.1", 80));

    bind(web, name, 80);

    JsonNode instance = get(servicePath(web)).getBody().get("instances").get(0);
    assertThat(instance.get("host").asString()).isEqualTo("127.0.0.1");
    assertThat(instance.get("port").asInt()).isEqualTo(hostPortOn(container, 80, "127.0.0.1"));
  }
}
