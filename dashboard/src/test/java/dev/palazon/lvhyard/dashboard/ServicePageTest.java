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

    @Test
    void publishingShowsTheHostnameAndTheState() {
        String web = newServiceName("web");
        register(web, Map.of("host", "10.1.2.3", "port", 8080));
        String page = "/services/ws-test/" + web;
        assertThat(getPage(page).getBody()).contains("Unpublished").contains("action=\"" + page + "/publish\"");

        ResponseEntity<String> response = postForm(page + "/publish", Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        assertThat(response.getHeaders().getLocation()).hasPath(page);
        assertThat(getPage(page).getBody()).contains("Unhealthy").contains("href=\"http://" + web + ".lvh.test\"")
                .contains("Local").contains("action=\"" + page + "/unpublish\"");
        assertThat(get(servicePath(web)).getBody().get("state").asString()).isEqualTo("unhealthy");
    }

    @Test
    void unpublishingKeepsTheInstances() {
        String web = newServiceName("web");
        register(web, Map.of("host", "10.1.2.3", "port", 8080));
        publish(web);
        String page = "/services/ws-test/" + web;

        ResponseEntity<String> response = postForm(page + "/unpublish", Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        assertThat(response.getHeaders().getLocation()).hasPath(page);
        assertThat(getPage(page).getBody()).contains("Unpublished").contains("10.1.2.3:8080")
                .doesNotContain(web + ".lvh.test");
    }

    @Test
    void unpublishingServiceWithoutInstancesGoesBackToTheCatalog() {
        String web = newServiceName("web");
        String id = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
        publish(web);
        delete(instancesPath(web) + "/" + id);
        assertThat(getPage("/services/ws-test/" + web).getBody()).contains("Unavailable");

        ResponseEntity<String> response = postForm("/services/ws-test/" + web + "/unpublish", Map.of());

        assertThat(response.getHeaders().getLocation()).hasPath("/");
    }

    @Test
    void publishingAnUnknownServiceIsNotFound() {
        ResponseEntity<String> response = postForm("/services/ws-test/" + newServiceName("nothing") + "/publish",
                Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
