package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** The edit form of a Static Instance, opened from a Service's page. */
class ServicePageEditComponentTest extends ServicePageComponentBase {

  @Test
  void editFormHoldsTheInstanceAsItIs() {
    String api = newServiceName("api");
    String id =
        register(
                api,
                Map.of(
                    "host",
                    "10.1.2.3",
                    "port",
                    8080,
                    "check",
                    Map.of("path", "/health", "interval", "5s"),
                    "tags",
                    List.of("a", "b"),
                    "meta",
                    Map.of("owner", "pablo")))
            .get("id")
            .asString();
    String editPath = pagePath(api) + "/instances/" + id + "/edit";
    assertThat(getPage(pagePath(api)).getBody()).contains("href=\"" + editPath + "\"");

    ResponseEntity<String> response = getPage(editPath);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(formTo(response.getBody(), editPath))
        .contains("value=\"10.1.2.3\"")
        .contains("value=\"8080\"")
        .contains("value=\"/health\"")
        .contains("value=\"5s\"")
        .contains("value=\"a, b\"")
        .contains("owner=pablo")
        .contains("<option value=\"http\" selected");
  }

  @Test
  void invalidEditIsShownInTheFormWithWhatWasTyped() {
    String api = newServiceName("api");
    String id = register(api, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();

    ResponseEntity<String> response =
        postForm(
            pagePath(api) + "/instances/" + id + "/edit",
            Map.of(
                "host",
                "10.1.2.3",
                "port",
                "8080",
                "check",
                "http",
                "path",
                "/",
                "interval",
                "often",
                "timeout",
                "",
                "tags",
                "",
                "meta",
                ""));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody())
        .contains("not a duration")
        .contains("value=\"often\"")
        .contains("aria-invalid=\"true\"");
    assertThat(
            get(servicePath(api))
                .getBody()
                .get("instances")
                .get(0)
                .get("check")
                .get("type")
                .asString())
        .isEqualTo("tcp");
  }

  @Test
  void editingAnUnknownInstanceIsNotFound() {
    String api = newServiceName("api");
    register(api, Map.of("port", 8080));

    assertThat(getPage(pagePath(api) + "/instances/nothing/edit").getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }
}
