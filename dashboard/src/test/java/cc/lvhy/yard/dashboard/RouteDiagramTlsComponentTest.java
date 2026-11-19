package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.json.JsonMapper;

/** The Route Diagram on a Workstation with every TLS Part on. */
@TestPropertySource(
    properties = {
      "lvh-yard.traefik.https-port=8443",
      "lvh-yard.traefik.client-auth=true",
      "lvh-yard.traefik.mtls=true"
    })
class RouteDiagramTlsComponentTest extends DashboardComponentTest {

  private static final JsonMapper JSON = new JsonMapper();

  @AfterEach
  void removeProfiles() throws Exception {
    removeAllRouteProfiles();
  }

  private String diagramOf(String profile) {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    put(publicationPath(web), Map.of("scope", "local", "profiles", List.of(profile)));
    return getPage("/services/ws-test/" + web + "/diagram").getBody();
  }

  @Test
  void aClientCertificateProfileLeavesOnlyHttps() throws Exception {
    putRouteProfiles(JSON.readTree("{\"tls-cert\": {\"clientCertificate\": {}}}"));

    assertThat(diagramOf("tls-cert"))
        .contains("TLS, Client Certificate")
        .contains("needs HTTPS")
        .doesNotContain("HTTPS is off");
  }

  @Test
  void aMutualTlsPublicationMakesTheStepToTheInstancesMutualTls() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    put(publicationPath(web), Map.of("scope", "local", "mutualTls", true));

    assertThat(getPage("/services/ws-test/" + web + "/diagram").getBody())
        .contains("Traefik to Instances: mutual TLS")
        .doesNotContain("Traefik to Instances: plain HTTP");
  }

  @Test
  void aPublicationWithoutMutualTlsMakesTheStepToTheInstancesPlainHttp() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 8080));
    put(publicationPath(web), Map.of("scope", "local"));

    assertThat(getPage("/services/ws-test/" + web + "/diagram").getBody())
        .contains("Traefik to Instances: plain HTTP")
        .doesNotContain("Traefik to Instances: mutual TLS");
  }
}
