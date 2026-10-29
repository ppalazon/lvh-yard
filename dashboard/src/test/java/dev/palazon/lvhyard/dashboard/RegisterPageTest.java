package dev.palazon.lvhyard.dashboard;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** The Register form, which adds a Static Instance. */
class RegisterPageTest extends DashboardTest {

    @Test
    void formAsksForNameHostAndPortAndShowsTheDefaultHost() {
        ResponseEntity<String> response = getPage("/register");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("name=\"name\"").contains("name=\"host\"").contains("name=\"port\"")
                .contains("placeholder=\"" + lanAddress() + "\"");
    }

    @Test
    void registeringLeadsToTheServicesPage() {
        String grafana = newServiceName("grafana");

        ResponseEntity<String> response = postForm("/register",
                Map.of("name", grafana, "host", "10.1.2.3", "port", "3000"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        assertThat(response.getHeaders().getLocation()).hasPath("/services/ws-test/" + grafana);
        assertThat(getPage("/services/ws-test/" + grafana).getBody()).contains("10.1.2.3:3000");
    }

    @Test
    void emptyHostRegistersAtTheLanAddress() {
        String api = newServiceName("api");

        postForm("/register", Map.of("name", api, "host", "", "port", "8080"));

        assertThat(getPage("/services/ws-test/" + api).getBody()).contains(lanAddress() + ":8080");
    }

    @Test
    void invalidNameIsShownInTheFormWithWhatWasTyped() {
        ResponseEntity<String> response = postForm("/register",
                Map.of("name", "My_API", "host", "10.1.2.3", "port", "8080"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("single DNS label").contains("aria-invalid=\"true\"")
                .contains("value=\"My_API\"").contains("value=\"10.1.2.3\"").contains("value=\"8080\"");
    }

    @Test
    void reservedNameIsShownInTheForm() {
        ResponseEntity<String> response = postForm("/register", Map.of("name", "yard", "host", "", "port", "8080"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("taken by the dashboard");
    }

    @Test
    void portThatIsNotANumberIsShownInTheForm() {
        String api = newServiceName("api");

        ResponseEntity<String> response = postForm("/register", Map.of("name", api, "host", "", "port", "http"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("The port http is not a TCP port").contains("value=\"http\"");
    }
}
