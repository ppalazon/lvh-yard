package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** The warning for an Instance that Traefik calls over plain HTTP. */
@TestPropertySource(
    properties = {
      "lvh-yard.traefik.https-port=8443",
      "lvh-yard.traefik.client-auth=true",
      "lvh-yard.traefik.mtls=true"
    })
class UnencryptedInstanceWarningComponentTest extends DashboardComponentTest {
  @Test
  void anHttpsPublicationWarnsAboutAnInstanceOffLoopbackThatTraefikCallsOverHttp() {
    // A Service with mutual TLS is the only one whose hop is encrypted.
    String lan = newServiceName("lan");
    register(lan, Map.of("host", "10.1.2.3", "port", 8080));
    String loopback = newServiceName("loopback");
    register(loopback, Map.of("host", "127.0.0.1", "port", 8080));
    String encrypted = newServiceName("encrypted");
    register(encrypted, Map.of("host", "10.1.2.3", "port", 8443));

    assertThat(put(publicationPath(lan), Map.of()).getBody().get("warnings").toString())
        .contains("unencrypted-instance", "10.1.2.3:8080");
    assertThat(put(publicationPath(loopback), Map.of()).getBody().get("warnings").toString())
        .doesNotContain("unencrypted-instance");
    assertThat(
            put(publicationPath(encrypted), Map.of("mutualTls", true))
                .getBody()
                .get("warnings")
                .toString())
        .doesNotContain("unencrypted-instance");
  }
}
