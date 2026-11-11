package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

@TestPropertySource(properties = "lvh-yard.traefik.https-port=8443")
class HttpsRoutesComponentTest extends DashboardComponentTest {
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
}
