package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@TestPropertySource(properties = "lvh-yard.traefik.https-port=8443")
class HttpsRoutesComponentTest extends DashboardComponentTest {
  @AfterEach
  void removeProfiles() throws Exception {
    removeAllRouteProfiles();
    remote.removeAllRouteProfiles();
  }

  @Test
  void clientCertificateAndChainsRequireHttpsWithoutCertificateMiddlewares() {
    putRouteProfiles(
        new JsonMapper()
            .readTree(
                """
                {"certificate": {"clientCertificate": {}},
                 "certificate-chain": {"chain": {"middlewares": ["certificate"]}},
                 "compressed": {"compress": {}},
                 "protected": {"chain": {"middlewares": ["certificate-chain", "compressed"]}}}
                """));
    for (String profile : List.of("certificate", "certificate-chain", "protected")) {
      String service = newServiceName("certificate");
      register(service, Map.of("host", "10.1.2.3", "port", 8080));
      JsonNode saved =
          put(publicationPath(service), Map.of("profiles", List.of(profile))).getBody();
      JsonNode http = get("/traefik/configuration").getBody().get("http");
      assertThat(http.get("routers").has(service)).isFalse();
      JsonNode secure = http.get("routers").get(service + "__https");
      assertThat(secure.get("entryPoints").toString()).isEqualTo("[\"websecure\"]");
      assertThat(secure.get("tls").get("options").asString())
          .isEqualTo("yard-client-certificate@file");
      assertThat(http.path("middlewares").has("ws-test_certificate")).isFalse();
      assertThat(http.path("middlewares").has("ws-test_certificate-chain")).isFalse();
      if (profile.equals("protected")) {
        assertThat(secure.get("middlewares").toString()).isEqualTo("[\"ws-test_protected\"]");
        assertThat(
                http.get("middlewares")
                    .get("ws-test_protected")
                    .get("chain")
                    .get("middlewares")
                    .toString())
            .isEqualTo("[\"ws-test_compressed\"]");
      } else {
        assertThat(secure.has("middlewares")).isFalse();
      }
      assertThat(saved.get("warnings").toString()).doesNotContain("client-certificate-unavailable");
      assertPublicationLink("ws-test", service, "https://" + service + ".lvh.test:8443");
    }
  }

  @Test
  void remoteClientCertificateRequirementUsesItsHomeProfiles() {
    remote.putRouteProfiles(
        new JsonMapper()
            .readTree(
                """
                {"remote-certificate": {"clientCertificate": {}},
                 "remote-chain": {"chain": {"middlewares": ["remote-certificate"]}}}
                """));
    for (String profile : List.of("remote-certificate", "remote-chain")) {
      String service = newServiceName("remote-certificate");
      remote.register(service, Map.of("host", "10.9.8.7", "port", 8080));
      remote.publish(service, Map.of("scope", "yard", "profiles", List.of(profile)));
      org.awaitility.Awaitility.await()
          .atMost(java.time.Duration.ofSeconds(10))
          .untilAsserted(() -> {
            JsonNode http = get("/traefik/configuration").getBody().get("http");
            String router = service + "." + RemoteWorkstation.NAME;
            assertThat(http.get("routers").has(router)).isFalse();
            assertThat(
                    http.get("routers")
                        .get(router + "__https")
                        .get("tls")
                        .get("options")
                        .asString())
                .isEqualTo("yard-client-certificate@file");
            assertThat(http.path("middlewares").has("ws-remote_remote-certificate")).isFalse();
            assertPublicationLink(
                "ws-remote", service, "https://" + service + ".ws-remote.lvh.test:8443");
          });
    }
  }

  @Test
  void routesHttpAndHttpsToTheSamePublicationWithoutRedirect() {
    String service = newServiceName("secure");
    register(service, Map.of("host", "10.1.2.3", "port", 8080));
    publish(service);
    JsonNode routers = get("/traefik/configuration").getBody().get("http").get("routers");
    JsonNode plain = routers.get(service);
    JsonNode secure = routers.get(service + "__https");
    assertThat(plain.get("entryPoints").get(0).asString()).isEqualTo("web");
    assertThat(plain.has("tls")).isFalse();
    assertThat(secure.get("entryPoints").get(0).asString()).isEqualTo("websecure");
    assertThat(secure.get("tls").isObject()).isTrue();
    assertThat(secure.get("rule")).isEqualTo(plain.get("rule"));
    assertThat(secure.get("service")).isEqualTo(plain.get("service"));
    assertThat(plain.has("middlewares")).isFalse();
  }

  private void assertPublicationLink(String home, String service, String url) {
    for (String path : List.of("/", "/services/" + home + "/" + service)) {
      assertThat(getPage(path).getBody()).contains("href=\"" + url + "\"");
    }
  }
}
