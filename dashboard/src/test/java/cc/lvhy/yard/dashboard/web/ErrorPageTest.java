package cc.lvhy.yard.dashboard.web;

import static org.assertj.core.api.Assertions.assertThat;

import cc.lvhy.yard.dashboard.Workstation;
import jakarta.servlet.RequestDispatcher;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

/** The page that replaces the Whitelabel page. It needs neither Consul nor Docker. */
class ErrorPageTest {

  private final ErrorPage errorPage = new ErrorPage(new Workstation("ws-test", "lvh.test"));

  private String page(MockHttpServletRequest request, HttpStatus status) throws Exception {
    ModelAndView view = errorPage.resolveErrorView(request, status, Map.of());
    assertThat(view.getStatus()).isEqualTo(status);
    MockHttpServletResponse response = new MockHttpServletResponse();
    view.getView().render(Map.of(), request, response);
    assertThat(response.getContentType()).startsWith("text/html");
    return response.getContentAsString();
  }

  private static MockHttpServletRequest failed(String method, String path, String message) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, "/error");
    request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, path);
    request.setAttribute(RequestDispatcher.ERROR_MESSAGE, message);
    return request;
  }

  @Test
  void consulThatDoesNotAnswerShowsHowToStartIt() throws Exception {
    MockHttpServletRequest request =
        failed("GET", "/route-profiles", "Consul does not answer at http://127.0.0.1:8500.");
    request.setAttribute(ErrorPage.CONSUL_URL, "http://127.0.0.1:8500");

    String page = page(request, HttpStatus.SERVICE_UNAVAILABLE);

    assertThat(page)
        .contains("<h1>Consul does not answer</h1>")
        .contains("Error 503")
        .contains("http://127.0.0.1:8500")
        .contains("curl http://127.0.0.1:8500/v1/status/leader")
        .contains("YARD_CONSUL_HTTP_PORT")
        .contains("href=\"/route-profiles\"")
        .contains("href=\"/status\"")
        .doesNotContain("Whitelabel");
  }

  @Test
  void otherFailureShowsItsMessage() throws Exception {
    String page =
        page(
            failed("GET", "/containers", "Docker does not answer."),
            HttpStatus.SERVICE_UNAVAILABLE);

    assertThat(page)
        .contains("<h1>A part of LVH Yard does not answer</h1>")
        .contains("role=\"alert\"")
        .contains("Docker does not answer.")
        .contains("read the dashboard log")
        .doesNotContain("/v1/status/leader");
  }

  @Test
  void failedPostOffersNoTryAgain() throws Exception {
    String page =
        page(failed("POST", "/backups/new", "Something broke"), HttpStatus.INTERNAL_SERVER_ERROR);

    assertThat(page).contains("<h1>Something went wrong</h1>").doesNotContain("Try again");
  }

  @Test
  void missingPageOffersNoTryAgainAndSaysWhatIsMissing() throws Exception {
    String page = page(failed("GET", "/nope", "No static resource nope."), HttpStatus.NOT_FOUND);

    assertThat(page)
        .contains("<h1>Page not found</h1>")
        .contains("There is nothing at /nope.")
        .doesNotContain("Try again")
        .doesNotContain("No static resource")
        .doesNotContain("role=\"alert\"");
  }

  @Test
  void missingThingWithItsOwnMessageShowsIt() throws Exception {
    String page =
        page(
            failed("GET", "/services/ws-test/web", "Service web does not exist."),
            HttpStatus.NOT_FOUND);

    assertThat(page)
        .contains("role=\"alert\"")
        .contains("Service web does not exist.")
        .doesNotContain("There is nothing at");
  }
}
