package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.services.HealthCheck;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.InvalidRegistrationException;
import dev.palazon.lvhyard.dashboard.services.Registrations;
import dev.palazon.lvhyard.dashboard.services.StaticInstanceRequest;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.EnumTypeInputType;
import org.xmlet.htmlapifaster.FlowContent;

/** Fields shared by the form that adds a Static Instance, the edit form and the binding form. */
final class InstanceForm {

  private InstanceForm() {}

  /** As typed. An empty field gets the default. */
  record Fields(
      String host,
      String port,
      String check,
      String path,
      String interval,
      String timeout,
      String tags,
      String meta) {

    static final Fields EMPTY = new Fields("", "", "tcp", "", "", "", "", "");

    /** Spring fills the fields by name. A field left out is empty, and the check is TCP. */
    Fields {
      host = orEmpty(host);
      port = orEmpty(port);
      check = check == null || check.isBlank() ? "tcp" : check;
      path = orEmpty(path);
      interval = orEmpty(interval);
      timeout = orEmpty(timeout);
      tags = orEmpty(tags);
      meta = orEmpty(meta);
    }

    private static String orEmpty(String value) {
      return value == null ? "" : value;
    }

    static Fields of(Instance instance) {
      HealthCheck check = instance.check();
      return new Fields(
          instance.host(),
          String.valueOf(instance.port()),
          check == null ? "tcp" : check.type(),
          check == null || check.path() == null ? "" : check.path(),
          check == null ? "" : check.interval(),
          check == null ? "" : check.timeout(),
          String.join(", ", instance.tags()),
          instance.meta().entrySet().stream()
              .map(entry -> entry.getKey() + "=" + entry.getValue())
              .collect(Collectors.joining("\n")));
    }

    /** The path counts only for an HTTP check. */
    StaticInstanceRequest request() {
      boolean http = "http".equals(check);
      return new StaticInstanceRequest(
          host.strip(),
          Registrations.parsePort(port),
          new HealthCheck(
              check.strip(), http ? path.strip() : null, interval.strip(), timeout.strip()),
          Registrations.parseTags(tags),
          Registrations.parseMeta(meta));
    }
  }

  /**
   * @param defaultHost the host an Instance gets when none is typed
   */
  static void fields(
      FlowContent<?, ?> parent,
      Fields fields,
      String defaultHost,
      InvalidRegistrationException error) {
    text(
        parent,
        "host",
        "Host",
        fields.host(),
        defaultHost,
        "Empty: this Workstation's LAN address, " + defaultHost + ".",
        error);
    text(parent, "port", "Port", fields.port(), "3000", null, error);
    field(
        parent,
        "check",
        "Health check",
        "TCP connects to the port. HTTP passes on a 2xx answer.",
        error,
        field ->
            field
                .select()
                .attrClass("select")
                .attrId("check")
                .attrName("check")
                .option()
                .attrValue("tcp")
                .of(option -> option.attrSelected(!fields.check().equals("http")))
                .text("TCP")
                .__()
                .option()
                .attrValue("http")
                .of(option -> option.attrSelected(fields.check().equals("http")))
                .text("HTTP")
                .__()
                .__());
    text(
        parent,
        "path",
        "HTTP path",
        fields.path(),
        "/",
        "Only for an HTTP check. Empty: /.",
        error);
    text(
        parent,
        "interval",
        "Interval",
        fields.interval(),
        "10s",
        "Such as 5s or 1m, at least 1s. Empty: 10s.",
        error);
    text(
        parent,
        "timeout",
        "Timeout",
        fields.timeout(),
        "2s",
        "Such as 500ms or 2s. Empty: 2s.",
        error);
    text(parent, "tags", "Tags", fields.tags(), "backend, v2", "Separated by commas.", error);
    field(
        parent,
        "meta",
        "Meta",
        "One key=value on each line.",
        error,
        field ->
            field
                .textarea()
                .attrClass("textarea")
                .attrId("meta")
                .attrName("meta")
                .attrRows(3)
                .addAttr("placeholder", "owner=pablo")
                .of(textarea -> {
                  if (isAbout(error, "meta")) {
                    textarea
                        .addAttr("aria-invalid", "true")
                        .addAttr("aria-describedby", "meta-error");
                  }
                })
                .text(fields.meta())
                .__());
  }

  static void text(
      FlowContent<?, ?> parent,
      String name,
      String label,
      String value,
      String placeholder,
      String help,
      InvalidRegistrationException error) {
    field(
        parent,
        name,
        label,
        help,
        error,
        field ->
            field
                .input()
                .attrType(EnumTypeInputType.TEXT)
                .attrClass("input")
                .attrId(name)
                .attrName(name)
                .attrValue(Layout.attr(value))
                .attrPlaceholder(Layout.attr(placeholder))
                .of(input -> {
                  if (isAbout(error, name)) {
                    input
                        .addAttr("aria-invalid", "true")
                        .addAttr("aria-describedby", name + "-error");
                  }
                })
                .__());
  }

  /** Shows the error, or else the help, below the control. */
  static void field(
      FlowContent<?, ?> parent,
      String name,
      String label,
      String help,
      InvalidRegistrationException error,
      Consumer<Div<?>> control) {
    boolean invalid = isAbout(error, name);
    parent
        .div()
        .attrClass("field")
        .of(field -> {
          if (invalid) {
            field.addAttr("data-invalid", "true");
          }
        })
        .label()
        .attrFor(name)
        .text(label)
        .__()
        .of(control::accept)
        .of(field -> {
          if (invalid) {
            field
                .p()
                .attrId(name + "-error")
                .addAttr("role", "alert")
                .text(error.getMessage())
                .__();
          } else if (help != null) {
            field.p().attrClass("text-muted-foreground text-sm").text(help).__();
          }
        })
        .__();
  }

  private static boolean isAbout(InvalidRegistrationException error, String name) {
    return error != null && error.field().equals(name);
  }
}
