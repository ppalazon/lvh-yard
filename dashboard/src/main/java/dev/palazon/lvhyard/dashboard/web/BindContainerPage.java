package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.ContainerBindingRequest;
import dev.palazon.lvhyard.dashboard.services.InvalidRegistrationException;
import dev.palazon.lvhyard.dashboard.services.Registrations;
import dev.palazon.lvhyard.dashboard.services.Service;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.EnumTypeInputType;
import org.xmlet.htmlapifaster.Input;
import org.xmlet.htmlapifaster.Ul;

/**
 * The form that binds a Service to a container or a Compose service. Its container field is a list
 * that you can also type in. Without bind.js, both fields are plain text fields.
 */
final class BindContainerPage {

  /** The one form field that names the target: a container, or {@code project/service}. */
  static final String TARGET = "target";

  static final String PORT = "containerPort";

  /**
   * One thing to bind, as the container field lists it.
   *
   * @param value what the field sends: a container name, or {@code project/service}
   * @param ports its Container Ports
   * @param followedBy the Service of the Binding that already names it, or {@code null}
   */
  record Target(String value, boolean compose, List<Integer> ports, String followedBy) {}

  /**
   * What the form sends. Spring fills the fields by name. A field left out is empty.
   *
   * @param target a container name, or a Compose project and service as {@code project/service}. A
   *     Docker container name has no {@code /}, so the two cannot mix.
   */
  record Bind(String target, String containerPort) {

    static final Bind EMPTY = new Bind("", "");

    Bind {
      target = target == null ? "" : target;
      containerPort = containerPort == null ? "" : containerPort;
    }

    ContainerBindingRequest request() {
      Integer port = Registrations.parsePort(PORT, containerPort);
      String typed = target.strip();
      int slash = typed.indexOf('/');
      return slash < 0
          ? ContainerBindingRequest.container(typed, port)
          : ContainerBindingRequest.compose(
              typed.substring(0, slash), typed.substring(slash + 1), port);
    }
  }

  private BindContainerPage() {}

  static String path(Service service) {
    return action(service) + "/new";
  }

  static String action(Service service) {
    return ServicePage.path(service) + "/container-bindings";
  }

  /**
   * @param targets Compose services first, then containers with no Compose project
   * @param dockerProblem why Docker cannot be read, or {@code null}
   * @param error why the last post failed, shown at its field, or {@code null}
   */
  static String render(
      Workstation workstation,
      Service service,
      List<Target> targets,
      String dockerProblem,
      Bind bind,
      InvalidRegistrationException error) {
    String title = "Bind a container to " + service.name();
    Optional<Target> picked =
        targets.stream().filter(target -> target.value().equals(bind.target().strip())).findFirst();
    return Layout.page(
        title,
        workstation.name(),
        main ->
            main.h1()
                .text(title)
                .__()
                .p()
                .text(
                    "The container does not need to run. Each container that matches gives an"
                        + " Instance.")
                .__()
                .of(content -> {
                  if (dockerProblem != null) {
                    content
                        .div()
                        .attrClass("alert max-w-md")
                        .addAttr("role", "status")
                        .h3()
                        .text("The dashboard cannot read Docker, so the list is empty")
                        .__()
                        .section()
                        .text(dockerProblem + " You can still type a name.")
                        .__()
                        .__();
                  }
                })
                .form()
                .attrClass("form flex max-w-md flex-col gap-6")
                .addAttr("method", "post")
                .addAttr("action", Layout.attr(action(service)))
                .addAttr("data-bind-form", "")
                .of(form ->
                    InstanceForm.field(
                        form,
                        TARGET,
                        "Container",
                        "A container name, or a Compose project and service as project/service.",
                        error,
                        field -> targetControl(field, targets, bind.target(), error)))
                .of(form ->
                    InstanceForm.field(
                        form,
                        PORT,
                        "Container Port",
                        "The port inside the container.",
                        error,
                        field -> portControl(field, picked, bind.containerPort(), error)))
                .div()
                .attrClass("flex gap-3")
                .button()
                .attrType(EnumTypeButtonType.SUBMIT)
                .attrClass("btn")
                .text("Bind")
                .__()
                .a()
                .attrClass("btn")
                .addAttr("data-variant", "outline")
                .attrHref(Layout.attr(ServicePage.path(service)))
                .text("Cancel")
                .__()
                .__()
                .__()
                .script()
                .attrSrc("/bind.js")
                .attrDefer(true)
                .__());
  }

  private static void targetControl(
      Div<?> field, List<Target> targets, String value, InvalidRegistrationException error) {
    field
        .div()
        .attrClass("relative")
        .input()
        .attrType(EnumTypeInputType.TEXT)
        .attrClass("input")
        .attrId(TARGET)
        .attrName(TARGET)
        .attrValue(Layout.attr(value))
        .attrPlaceholder("shop/web")
        .addAttr("autocomplete", "off")
        .addAttr("role", "combobox")
        .addAttr("aria-autocomplete", "list")
        .addAttr("aria-expanded", "false")
        .addAttr("aria-controls", TARGET + "-options")
        .of(input -> invalid(input, TARGET, error))
        .__()
        .ul()
        .attrId(TARGET + "-options")
        .attrClass(
            "bg-popover text-popover-foreground absolute z-10 mt-1 max-h-72 w-full overflow-auto"
                + " rounded-md border p-1 shadow-md")
        .addAttr("role", "listbox")
        .addAttr("aria-label", "Containers and Compose services")
        .addAttr("hidden", "")
        .of(list -> group(list, "Compose services", targets.stream().filter(Target::compose)))
        .of(list -> group(list, "Containers", targets.stream().filter(t -> !t.compose())))
        .__()
        .__();
  }

  private static void group(Ul<?> list, String heading, Stream<Target> targets) {
    List<Target> members = targets.toList();
    if (members.isEmpty()) {
      return;
    }
    list.li()
        .attrClass("text-muted-foreground px-2 py-1 text-xs font-medium")
        .addAttr("role", "presentation")
        .text(heading)
        .__();
    for (Target target : members) {
      boolean followed = target.followedBy() != null;
      list.li()
          .attrClass(
              "flex cursor-default justify-between gap-2 rounded-sm px-2 py-1.5 text-sm"
                  + " aria-disabled:opacity-50 aria-selected:bg-accent")
          .addAttr("role", "option")
          .addAttr("data-value", Layout.attr(target.value()))
          .addAttr("data-ports", Layout.attr(ports(target.ports(), " ")))
          .of(option -> {
            if (followed) {
              option.addAttr("aria-disabled", "true");
            }
          })
          .span()
          .text(target.value())
          .__()
          .span()
          .attrClass("text-muted-foreground")
          .text(
              followed
                  ? "followed by " + target.followedBy()
                  : target.ports().isEmpty() ? "" : ports(target.ports(), ", "))
          .__()
          .__();
    }
  }

  /** The list holds the ports of the pick. bind.js fills it again when the pick changes. */
  private static void portControl(
      Div<?> field, Optional<Target> picked, String value, InvalidRegistrationException error) {
    field
        .input()
        .attrType(EnumTypeInputType.TEXT)
        .attrClass("input")
        .attrId(PORT)
        .attrName(PORT)
        .attrValue(Layout.attr(value))
        .attrPlaceholder("80")
        .addAttr("inputmode", "numeric")
        .addAttr("autocomplete", "off")
        .addAttr("list", PORT + "-options")
        .of(input -> invalid(input, PORT, error))
        .__()
        .datalist()
        .attrId(PORT + "-options")
        .of(list ->
            picked.stream()
                .flatMap(target -> target.ports().stream())
                .forEach(port -> list.option().attrValue(String.valueOf(port)).__()))
        .__();
  }

  private static void invalid(Input<?> input, String name, InvalidRegistrationException error) {
    if (error != null && error.field().equals(name)) {
      input.addAttr("aria-invalid", "true").addAttr("aria-describedby", name + "-error");
    }
  }

  private static String ports(List<Integer> ports, String separator) {
    return ports.stream().map(String::valueOf).collect(Collectors.joining(separator));
  }
}
