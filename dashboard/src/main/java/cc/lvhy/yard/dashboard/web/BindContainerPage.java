package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.services.ContainerBinding;
import cc.lvhy.yard.dashboard.services.ContainerBindingRequest;
import cc.lvhy.yard.dashboard.services.InstanceRules;
import cc.lvhy.yard.dashboard.services.InvalidRegistrationException;
import cc.lvhy.yard.dashboard.services.Service;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.xmlet.htmlapifaster.Div;
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
   * @param followedBy the Services that have a Binding that names it, by name
   * @param running how many of its containers run
   * @param total how many containers it has: one for a container, one or more for a Compose service
   */
  record Target(
      String value,
      boolean compose,
      List<TargetPort> ports,
      List<String> followedBy,
      int running,
      int total) {

    /**
     * {@code running}, {@code stopped}, or {@code 1 of 2 running} when only some containers run.
     */
    String state() {
      if (running == 0) {
        return "stopped";
      }
      return running == total ? "running" : running + " of " + total + " running";
    }

    /** The badge variant of {@link #state()}. */
    String variant() {
      if (running == 0) {
        return "secondary";
      }
      return running == total ? "success" : "outline";
    }
  }

  /**
   * @param hostPorts the host ports that publish it, none when it is only exposed
   */
  record TargetPort(int port, List<Integer> hostPorts) {}

  /**
   * What the form sends. Spring fills the fields by name. A field left out is empty.
   *
   * @param target a container name, or a Compose project and service as {@code project/service}. A
   *     Docker container name has no {@code /}, so the two cannot mix.
   */
  record Bind(String target, String containerPort) {

    static final Bind EMPTY = new Bind("", "");

    /** The fields of the edit form, filled with the Binding as it is. */
    static Bind of(ContainerBinding binding) {
      return new Bind(
          binding.container() != null
              ? binding.container()
              : binding.composeProject() + "/" + binding.composeService(),
          String.valueOf(binding.port()));
    }

    Bind {
      target = target == null ? "" : target;
      containerPort = containerPort == null ? "" : containerPort;
    }

    ContainerBindingRequest request() {
      Integer port = InstanceRules.parsePort(PORT, containerPort);
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

  static String editPath(Service service, ContainerBinding binding) {
    return action(service) + "/" + binding.id() + "/edit";
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
    FormPage.Spec spec =
        new FormPage.Spec(
            null,
            "Bind a container to " + service.name(),
            "The container does not need to run. Each container that matches gives an Instance.",
            path(service),
            action(service),
            "Bind");
    return form(workstation, service, spec, targets, dockerProblem, bind, error);
  }

  /**
   * The same form for a Binding that exists. It stays in its place of the Instance Priority, and
   * the Bound Instances follow the new target and port.
   */
  static String renderEdit(
      Workstation workstation,
      Service service,
      ContainerBinding binding,
      List<Target> targets,
      String dockerProblem,
      Bind bind,
      InvalidRegistrationException error) {
    FormPage.Spec spec =
        new FormPage.Spec(
            null,
            "Edit a Container Binding of " + service.name(),
            "Now bound to " + binding.follows() + ", port " + binding.port() + ".",
            editPath(service, binding),
            editPath(service, binding),
            "Save");
    return form(workstation, service, spec, targets, dockerProblem, bind, error);
  }

  private static String form(
      Workstation workstation,
      Service service,
      FormPage.Spec spec,
      List<Target> targets,
      String dockerProblem,
      Bind bind,
      InvalidRegistrationException error) {
    return FormPage.render(
        workstation,
        spec,
        ServicePage.path(service),
        form -> form.addAttr("data-bind-form", ""),
        section -> {
          if (dockerProblem != null) {
            section
                .div()
                .attrClass("alert")
                .addAttr("role", "status")
                .h3()
                .text("The dashboard cannot read Docker, so the list is empty")
                .__()
                .section()
                .text(dockerProblem + " You can still type a name.")
                .__()
                .__();
          }
          InstanceForm.field(
              section,
              TARGET,
              "Container",
              "A container name, or a Compose project and service as project/service.",
              error,
              field -> targetControl(field, targets, bind.target(), error));
          InstanceForm.field(
              section,
              PORT,
              "Container Port",
              "The port inside the container. Pick one of its open ports, or type one.",
              error,
              field -> portControl(field, bind.containerPort(), error));
          section.script().attrSrc("/bind.js").attrDefer(true).__();
        });
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
      boolean used = !target.followedBy().isEmpty();
      list.li()
          .attrClass(
              "flex cursor-default items-center justify-between gap-2 rounded-sm py-1.5 pr-2 pl-5"
                  + " text-sm aria-selected:bg-accent")
          .addAttr("role", "option")
          .addAttr("data-value", Layout.attr(target.value()))
          .addAttr("data-ports", Layout.attr(dataPorts(target.ports())))
          .span()
          .attrClass("flex items-center gap-2")
          .span()
          .text(target.value())
          .__()
          .span()
          .attrClass("badge")
          .addAttr("data-variant", target.variant())
          .text(target.state())
          .__()
          .__()
          .span()
          .attrClass("text-muted-foreground")
          .text(
              used
                  ? "used by " + String.join(", ", target.followedBy())
                  : target.ports().isEmpty() ? "" : portNumbers(target.ports()))
          .__()
          .__();
    }
  }

  /** The list is empty until bind.js fills it with the ports of the target. */
  private static void portControl(Div<?> field, String value, InvalidRegistrationException error) {
    field
        .div()
        .attrClass("relative")
        .input()
        .attrType(EnumTypeInputType.TEXT)
        .attrClass("input")
        .attrId(PORT)
        .attrName(PORT)
        .attrValue(Layout.attr(value))
        .attrPlaceholder("80")
        .addAttr("inputmode", "numeric")
        .addAttr("autocomplete", "off")
        .addAttr("role", "combobox")
        .addAttr("aria-autocomplete", "list")
        .addAttr("aria-expanded", "false")
        .addAttr("aria-controls", PORT + "-options")
        .of(input -> invalid(input, PORT, error))
        .__()
        .ul()
        .attrId(PORT + "-options")
        .attrClass(
            "bg-popover text-popover-foreground absolute z-10 mt-1 max-h-72 w-full overflow-auto"
                + " rounded-md border p-1 shadow-md")
        .addAttr("role", "listbox")
        .addAttr("aria-label", "Container Ports")
        .addAttr("hidden", "")
        .__()
        .__();
  }

  private static void invalid(Input<?> input, String name, InvalidRegistrationException error) {
    if (error != null && error.field().equals(name)) {
      input.addAttr("aria-invalid", "true").addAttr("aria-describedby", name + "-error");
    }
  }

  /** Each port as {@code 80}, or {@code 80:8080,8081} when it is published on host ports. */
  private static String dataPorts(List<TargetPort> ports) {
    return ports.stream()
        .map(port ->
            port.hostPorts().isEmpty()
                ? String.valueOf(port.port())
                : port.port() + ":" + ports(port.hostPorts(), ","))
        .collect(Collectors.joining(" "));
  }

  private static String portNumbers(List<TargetPort> ports) {
    return ports(ports.stream().map(TargetPort::port).toList(), ", ");
  }

  private static String ports(List<Integer> ports, String separator) {
    return ports.stream().map(String::valueOf).collect(Collectors.joining(separator));
  }
}
