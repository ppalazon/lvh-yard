package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** The error page for browsers, and the JSON error that other clients still get. */
class ErrorPagesComponentTest extends DashboardComponentTest {

  @Test
  void browserGetsTheDashboardErrorPage() {
    ResponseEntity<String> response = getAccepting("/no-such-page", MediaType.TEXT_HTML);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_HTML))
        .isTrue();
    assertThat(response.getBody())
        .contains("<h1>Page not found</h1>")
        .contains("href=\"/status\"")
        .doesNotContain("Whitelabel");
  }

  @Test
  void otherClientsStillGetJson() {
    ResponseEntity<String> response = getAccepting("/no-such-page", MediaType.APPLICATION_JSON);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).doesNotContain("<html").contains("404");
  }
}
