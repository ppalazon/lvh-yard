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
import org.xmlet.htmlapifaster.FlowContent;
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
    boolean generic = status == HttpStatus.NOT_FOUND && isSpringNotFound(message);
    return Layout.page(
        title,
        workstation.name(),
        main ->
            main.div()
                .attrClass("flex max-w-3xl flex-col gap-6")
                .div()
                .attrClass("flex flex-col gap-2")
                .span()
                .attrClass("badge w-fit")
                .addAttr("data-variant", status.is5xxServerError() ? "destructive" : "outline")
                .text("Error " + status.value() + " · " + status.getReasonPhrase())
                .__()
                .h1()
                .text(title)
                .__()
                .__()
                .of(content -> {
                  if (generic) {
                    content
                        .p()
                        .attrClass("text-muted-foreground")
                        .text(
                            path == null
                                ? "There is nothing at this address."
                                : "There is nothing at " + path + ".")
                        .__();
                  } else if (message != null && consulUrl == null) {
                    // The Consul steps name the address, so they replace the message.
                    content
                        .div()
                        .attrClass("alert")
                        .addAttr("data-variant", "destructive")
                        .addAttr("role", "alert")
                        .section()
                        .text(message)
                        .__()
                        .__();
                  }
                  if (consulUrl != null) {
                    consulSteps(content, consulUrl);
                  } else if (status.is5xxServerError()) {
                    content
                        .p()
                        .attrClass("text-muted-foreground")
                        .text(
                            "The Status page shows which part of LVH Yard does not work. If the"
                                + " problem stays, read the dashboard log.")
                        .__();
                  }
                  actions(content, status, path);
                })
                .__());
  }

  /** Spring's own text for an address that no handler serves. It says nothing to a person. */
  private static boolean isSpringNotFound(String message) {
    return message == null
        || message.startsWith("No static resource ")
        || message.startsWith("No endpoint ");
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

  /** The dashboard knows Consul only by its HTTP API, so the steps name no tool that runs it. */
  private static void consulSteps(FlowContent<?, ?> parent, String consulUrl) {
    parent
        .p()
        .attrClass("text-muted-foreground")
        .text(
            "The dashboard reads and writes the Services, Publications and Aliases through the"
                + " HTTP API of the Consul of this Workstation, at "
                + consulUrl
                + ". That address does not answer, so the dashboard cannot show or change them.")
        .__();
    parent
        .div()
        .attrClass("card flex flex-col gap-3 p-4")
        .h2()
        .text("To find the problem")
        .__()
        .ol()
        .attrClass("list-decimal space-y-3 pl-5")
        .of(list -> step(list, "Make sure that Consul runs on this Workstation.", null))
        .of(list ->
            step(
                list,
                "Make sure that its HTTP API answers. This request returns the address of the"
                    + " leader:",
                "curl " + consulUrl + "/v1/status/leader"))
        .of(list ->
            step(
                list,
                "If Consul listens on another port, set YARD_CONSUL_HTTP_PORT for the dashboard"
                    + " and start the dashboard again.",
                null))
        .__()
        .p()
        .attrClass("text-muted-foreground text-sm")
        .text("After a start, Consul needs a few seconds to elect itself leader.")
        .__()
        .__();
  }

  /**
   * @param command or {@code null}
   */
  private static void step(Ol<?> list, String text, String command) {
    // A flex item gets no list number, so the item stays a block.
    list.li()
        .span()
        .attrClass("block")
        .text(text)
        .__()
        .of(item -> {
          if (command != null) {
            item.code()
                .attrClass(
                    "mt-1 block w-fit max-w-full break-all rounded bg-muted px-2 py-1 font-mono"
                        + " text-sm")
                .text(command)
                .__();
          }
        })
        .__();
  }

  /**
   * A 404 has nothing to try again. The first button is the one to click: Try again, or else the
   * Catalog. After a failure, the Status page comes before the Catalog, because the Catalog may
   * fail too.
   */
  private static void actions(FlowContent<?, ?> parent, HttpStatus status, String path) {
    boolean retry = status != HttpStatus.NOT_FOUND && path != null;
    parent
        .div()
        .attrClass("flex flex-wrap gap-3")
        .of(buttons -> {
          if (retry) {
            buttons.a().attrClass("btn").attrHref(Layout.attr(path)).text("Try again").__();
            link(buttons, StatusPage.PATH, "Status", true);
            link(buttons, "/", "Catalog", true);
          } else {
            link(buttons, "/", "Catalog", false);
            link(buttons, StatusPage.PATH, "Status", true);
          }
        })
        .__();
  }

  private static void link(FlowContent<?, ?> parent, String href, String text, boolean outline) {
    parent
        .a()
        .attrClass("btn")
        .of(link -> {
          if (outline) {
            link.addAttr("data-variant", "outline");
          }
        })
        .attrHref(href)
        .text(text)
        .__();
  }
}
