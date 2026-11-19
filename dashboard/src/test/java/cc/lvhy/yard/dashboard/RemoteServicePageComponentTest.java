package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The page of a Service of the other Workstation: the same cards, with no buttons to change. */
class RemoteServicePageComponentTest extends ServicePageComponentBase {

  @Test
  void remoteServiceShowsTheSameCardsAndAlertsWithoutChangeButtons() {
    String api = newServiceName("api");
    String first =
        remote.register(api, Map.of("host", "10.9.8.7", "port", 9090)).get("id").asString();
    String second =
        remote.register(api, Map.of("host", "10.9.8.7", "port", 9091)).get("id").asString();
    remote.setInstancePriority(api, Map.of("order", List.of(second)));
    remote.publish(api, "yard");

    String body = getPage(remotePagePath(api)).getBody();

    assertThat(div(body, "role=\"alert\"")).contains("No Instance passes its checks");
    assertThat(div(body, "id=\"sections\"")).contains(">Publication</h2>");
    assertThat(body)
        .contains("Yard Scope")
        .contains(api + "." + RemoteWorkstation.NAME + ".lvh.test");
    assertInOrder(
        div(body, "id=\"sources\""),
        "data-source=\"" + second + "\"",
        "data-source=\"" + first + "\"");
    assertThat(div(body, "id=\"service\"")).doesNotContain("<form").doesNotContain("<button");
  }

  @Test
  void remoteServicesContainerBindingsAreShownWithoutAForm() {
    String web = newServiceName("web");
    String name = newContainerName();
    remote.bind(web, name, 80);

    String page = getPage(remotePagePath(web)).getBody();

    assertThat(page).contains(name).doesNotContain("/container-bindings/");
  }
}
