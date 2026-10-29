package dev.palazon.lvhyard.dashboard;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** A Service's detail page, where its Instances are seen and removed. */
class ServicePageTest extends DashboardTest {

    @Test
    void showsTheServicesInstancesAndTheirHealth() {
        String api = newServiceName("api");
        register(api, Map.of("host", "10.1.2.3", "port", 8080));

        ResponseEntity<String> response = getPage("/services/ws-test/" + api);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("<h1>" + api + "</h1>").contains("10.1.2.3:8080")
                .contains("critical");
    }

    @Test
    void removingAnInstanceStaysOnTheServicesPageWhileOthersAreLeft() {
        String web = newServiceName("web");
        String first = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
        register(web, Map.of("host", "10.1.2.3", "port", 8081));
        String removePath = "/services/ws-test/" + web + "/instances/" + first + "/remove";
        assertThat(getPage("/services/ws-test/" + web).getBody()).contains("action=\"" + removePath + "\"");

        ResponseEntity<String> response = postForm(removePath, Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        assertThat(response.getHeaders().getLocation()).hasPath("/services/ws-test/" + web);
        assertThat(getPage("/services/ws-test/" + web).getBody()).doesNotContain("10.1.2.3:8080")
                .contains("10.1.2.3:8081");
    }

    @Test
    void removingTheLastInstanceGoesBackToTheCatalog() {
        String web = newServiceName("web");
        String id = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();

        ResponseEntity<String> response = postForm("/services/ws-test/" + web + "/instances/" + id + "/remove",
                Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        assertThat(response.getHeaders().getLocation()).hasPath("/");
        assertThat(getPage("/").getBody()).doesNotContain(web);
    }

    @Test
    void unknownServiceIsNotFound() {
        ResponseEntity<String> response = getPage("/services/ws-test/" + newServiceName("nothing"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
