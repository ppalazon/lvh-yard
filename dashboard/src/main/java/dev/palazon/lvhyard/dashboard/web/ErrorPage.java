package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorViewResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.xmlet.htmlapifaster.Main;
import org.xmlet.htmlapifaster.Ol;

/**
 * A browser gets this page for a failed request, in place of the Whitelabel page of Spring Boot.
 * Spring Boot's own {@code /error} still answers other clients with JSON. The page uses only the
 * request and the settings, so it shows while Consul or Docker does not answer.
 */
@Component
public class ErrorPage implements ErrorViewResolver {

  /**
   * The request attribute that {@code Problems} sets to the Consul address when Consul did not
   * answer. The page then shows how to start Consul.
   */
  public static final String CONSUL_URL = ErrorPage.class.getName() + ".consul-url";

  private final Workstation workstation;

  ErrorPage(Workstation workstation) {
    this.workstation = workstation;
  }

  @Override
  public ModelAndView resolveErrorView(
      HttpServletRequest request, HttpStatus status, Map<String, Object> model) {
    String page =
        render(
            workstation,
            status,
            text(request.getAttribute(RequestDispatcher.ERROR_MESSAGE)),
            "GET".equals(request.getMethod())
                ? text(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI))
                : null,
            text(request.getAttribute(CONSUL_URL)));
    View view =
        (ignored, viewRequest, response) -> {
          response.setContentType(Layout.HTML.toString());
          response.getOutputStream().write(page.getBytes(StandardCharsets.UTF_8));
        };
    ModelAndView answer = new ModelAndView(view);
    answer.setStatus(status);
    return answer;
  }

  private static String text(Object value) {
    return value == null || value.toString().isBlank() ? null : value.toString();
  }

  /**
   * @param message why the request failed, or {@code null}
   * @param path the address to load again, or {@code null}. A failed post has none, because a link
   *     cannot post again.
   * @param consulUrl the Consul address when Consul did not answer, or {@code null}
   */
  static String render(
      Workstation workstation, HttpStatus status, String message, String path, String consulUrl) {
    String title = title(status, consulUrl);
    return Layout.page(
        title,
        workstation.name(),
        main -> {
          main.div()
              .attrClass("flex flex-col gap-2")
              .p()
              .attrClass("text-muted-foreground text-sm")
              .text("Error " + status.value() + " · " + status.getReasonPhrase())
              .__()
              .h1()
              .text(title)
              .__()
              .__();
          // The Consul steps name the address, so they replace the message.
          if (message != null && consulUrl == null) {
            main.div()
                .attrClass("alert")
                .addAttr("data-variant", "destructive")
                .addAttr("role", "alert")
                .section()
                .text(message)
                .__()
                .__();
          }
          if (consulUrl != null) {
            consulSteps(main, consulUrl);
          } else if (status.is5xxServerError()) {
            main.p()
                .text(
                    "The Status page shows which part of LVH Yard does not work. If the problem"
                        + " stays, run docker compose logs dashboard in the Workstation Folder to"
                        + " read the dashboard log.")
                .__();
          }
          actions(main, status, path);
        });
  }

  private static String title(HttpStatus status, String consulUrl) {
    if (consulUrl != null) {
      return "Consul does not answer";
    }
    return switch (status) {
      case NOT_FOUND -> "Page not found";
      case BAD_REQUEST -> "The request is not valid";
      case CONFLICT -> "The change conflicts with the current state";
      case SERVICE_UNAVAILABLE -> "A part of LVH Yard does not answer";
      default -> status.is5xxServerError() ? "Something went wrong" : "The request failed";
    };
  }

  private static void consulSteps(Main<?> main, String consulUrl) {
    main.p()
        .text(
            "The dashboard keeps the Services, Publications and Aliases in the Consul of "
                + "this Workstation, at "
                + consulUrl
                + ". Without Consul, the dashboard cannot show or change them.")
        .__()
        .div()
        .attrClass("card flex flex-col gap-3 p-4")
        .h2()
        .text("To start Consul again")
        .__()
        .p()
        .text("Run these commands in the Workstation Folder, where compose.yaml is.")
        .__()
        .ol()
        .attrClass("list-decimal space-y-3 pl-5")
        .of(list -> step(list, "To see whether Consul runs:", "docker compose ps consul"))
        .of(list -> step(list, "If it does not run, start it:", "docker compose up -d consul"))
        .of(list -> step(list, "If it stops again, read why:", "docker compose logs consul"))
        .__()
        .p()
        .attrClass("text-muted-foreground text-sm")
        .text("Consul needs a few seconds to start and to elect itself leader.")
        .__()
        .__();
  }

  private static void step(Ol<?> list, String text, String command) {
    // A flex item gets no list number, so the item stays a block.
    list.li()
        .span()
        .attrClass("block")
        .text(text)
        .__()
        .code()
        .attrClass("mt-1 block w-fit rounded bg-muted px-2 py-1 font-mono text-sm")
        .text(command)
        .__()
        .__();
  }

  /** A 404 has nothing to try again. */
  private static void actions(Main<?> main, HttpStatus status, String path) {
    main.div()
        .attrClass("flex flex-wrap gap-3")
        .of(buttons -> {
          if (status != HttpStatus.NOT_FOUND && path != null) {
            buttons.a().attrClass("btn").attrHref(Layout.attr(path)).text("Try again").__();
          }
        })
        .a()
        .attrClass("btn")
        .addAttr("data-variant", "outline")
        .attrHref(StatusPage.PATH)
        .text("Status")
        .__()
        .a()
        .attrClass("btn")
        .addAttr("data-variant", "outline")
        .attrHref("/")
        .text("Catalog")
        .__()
        .__();
  }
}
