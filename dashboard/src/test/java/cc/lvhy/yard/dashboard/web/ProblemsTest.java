package cc.lvhy.yard.dashboard.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cc.lvhy.yard.dashboard.consul.ConsulSettings;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

class ProblemsTest {

  @RestController
  static class Failing {
    @GetMapping({"/api/unanswered", "/unanswered"})
    String unanswered() {
      throw new ResourceAccessException("I/O error on GET request: Request cancelled");
    }

    @GetMapping({"/api/broken", "/broken"})
    String broken() {
      throw new IllegalStateException("Something broke");
    }

    @GetMapping("/missing")
    String missing() {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
  }

  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new Failing())
          .setControllerAdvice(
              new Problems(
                  new ConsulSettings(
                      URI.create("http://127.0.0.1:8500"),
                      "",
                      Duration.ofSeconds(3),
                      Duration.ofSeconds(10)),
                  false))
          .build();

  @Test
  void theApiAnswersAnUnansweredConsulWithProblemDetails() throws Exception {
    mvc.perform(get("/api/unanswered"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(
            jsonPath("$.detail")
                .value("Consul does not answer at http://127.0.0.1:8500. Try again in a moment."));
  }

  @Test
  void aPageAnswersAnUnansweredConsulWithTheErrorPage() throws Exception {
    mvc.perform(get("/unanswered"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(request().attribute(ErrorPage.CONSUL_URL, "http://127.0.0.1:8500"));
  }

  @Test
  void theApiAnswersAnyOtherExceptionWithProblemDetails() throws Exception {
    mvc.perform(get("/api/broken"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.detail").value("Something broke"));
  }

  @Test
  void aPageAnswersAnyOtherExceptionWithTheErrorPage() throws Exception {
    mvc.perform(get("/broken")).andExpect(status().isInternalServerError());
  }

  @Test
  void anExceptionWithItsOwnStatusKeepsIt() throws Exception {
    mvc.perform(get("/missing")).andExpect(status().isNotFound());
  }
}
