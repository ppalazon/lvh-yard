package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.ContainerBindingRequest;
import dev.palazon.lvhyard.dashboard.services.InvalidRegistrationException;
import dev.palazon.lvhyard.dashboard.services.Registrations;
import dev.palazon.lvhyard.dashboard.services.Service;
import dev.palazon.lvhyard.dashboard.services.UnfollowedContainer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.EnumTypeInputType;
import org.xmlet.htmlapifaster.Main;
import org.xmlet.htmlapifaster.Td;
import org.xmlet.htmlapifaster.Tr;

/** The running containers that no Container Binding follows. The list refreshes itself. */
final class ContainersPage {

  static final String PATH = "/containers";

  static final String BIND_PATH = PATH + "/bind";

  static final String CONTAINER = "container";

  static final String COMPOSE = "compose";

  record RegisterFailure(String container, String reason) {}

  /**
   * What the binding form sends, besides {@code publish}. Spring fills the fields by name. A field
   * left out is empty.
   *
   * @param kind {@value #CONTAINER} or {@value #COMPOSE}, by default {@value #CONTAINER}
   * @param name the Service name
   */
  record Binding(
      String kind,
      String container,
      String composeProject,
      String composeService,
      String containerPort,
      String name) {

    static final Binding EMPTY = new Binding(CONTAINER, "", "", "", "", "");

    Binding {
      kind = COMPOSE.equals(kind) ? COMPOSE : CONTAINER;
      container = container == null ? "" : container;
      composeProject = composeProject == null ? "" : composeProject;
      composeService = composeService == null ? "" : composeService;
      containerPort = containerPort == null ? "" : containerPort;
      name = name == null ? "" : name;
    }

    /** The other kind's fields are left out. */
    ContainerBindingRequest request(boolean publish) {
      Integer port = Registrations.parsePort("containerPort", containerPort);
      return kind.equals(COMPOSE)
          ? new ContainerBindingRequest(null, composeProject, composeService, port, publish)
          : new ContainerBindingRequest(container, null, null, port, publish);
    }
  }

  /**
   * What the binding form shows.
   *
   * @param error why the last binding failed, shown at its field, or {@code null}
   */
  record BindForm(Binding binding, boolean publish, InvalidRegistrationException error) {

    static final BindForm EMPTY = new BindForm(Binding.EMPTY, true, null);
  }

  private ContainersPage() {}

  static String registerPath(String container) {
    return PATH + "/" + container + "/register";
  }

  /**
   * @param containers in the order to show them, Compose projects first
   * @param dockerProblem why Docker cannot be read, or {@code null} when it answered
   * @param registered the Service that the one-click Register just gave a container, or {@code
   *     null}. It is said outside the refreshed list, so the refresh keeps it.
   * @param failure the register that failed, or {@code null}
   */
  static String render(
      Workstation workstation,
      List<UnfollowedContainer> containers,
      String dockerProblem,
      Service registered,
      RegisterFailure failure,
      BindForm bind) {
    return Layout.page(
        "Containers",
        workstation.name(),
        Layout.Section.CONTAINERS,
        main ->
            main.h1()
                .text("Containers")
                .__()
                .p()
                .text("Running containers that LVH Yard does not follow yet.")
                .__()
                .of(content -> {
                  if (registered != null) {
                    content
                        .div()
                        .attrClass("alert")
                        .addAttr("data-registered", Layout.attr(registered.name()))
                        .addAttr("role", "status")
                        .section()
                        .text("Registered in the Service ")
                        .a()
                        .attrClass("underline")
                        .attrHref(Layout.attr(ServicePage.path(registered)))
                        .text(registered.name())
                        .__()
                        .text(".")
                        .__()
                        .__();
                  }
                })
                .of(content -> {
                  if (failure == null) {
                    Layout.refreshed(
                        content,
                        "containers",
                        PATH,
                        region -> list(region, containers, dockerProblem, null));
                    return;
                  }
                  // A refresh would drop the failure, so this list stays as it is.
                  content
                      .div()
                      .attrId("containers")
                      .attrClass("flex flex-col gap-8")
                      .of(region -> list(region, containers, dockerProblem, failure))
                      .__();
                })
                .of(content -> bindForm(content, bind)));
  }

  /**
   * Outside the refreshed list, so the refresh cannot reset what is typed. app.css hides the fields
   * of the kind that is not chosen, so the choice works without JavaScript.
   */
  private static void bindForm(Main<?> main, BindForm bind) {
    Binding binding = bind.binding();
    InvalidRegistrationException error = bind.error();
    main.h2()
        .text("Bind a container or a Compose service")
        .__()
        .p()
        .text("The container does not need to run. Each container that matches gets an Instance.")
        .__()
        .form()
        .attrClass("form flex max-w-md flex-col gap-6")
        .addAttr("data-bind-form", "")
        .addAttr("method", "post")
        .addAttr("action", BIND_PATH)
        .div()
        .attrClass("flex flex-col gap-3")
        .addAttr("role", "radiogroup")
        .addAttr("aria-label", "What to bind")
        .of(group -> kind(group, CONTAINER, "Container name", binding.kind()))
        .of(group -> kind(group, COMPOSE, "Compose service", binding.kind()))
        .__()
        .of(form ->
            InstanceForm.text(
                form,
                "container",
                "Container name",
                binding.container(),
                "grafana",
                "As docker run --name gives it.",
                error))
        .of(form ->
            InstanceForm.text(
                form,
                "composeProject",
                "Compose project",
                binding.composeProject(),
                "shop",
                null,
                error))
        .of(form ->
            InstanceForm.text(
                form,
                "composeService",
                "Compose service",
                binding.composeService(),
                "web",
                "Each of its containers gets an Instance.",
                error))
        .of(form ->
            InstanceForm.text(
                form,
                "containerPort",
                "Container port",
                binding.containerPort(),
                "80",
                "The port inside the container.",
                error))
        .of(form ->
            InstanceForm.text(
                form,
                "name",
                "Service name",
                binding.name(),
                "web",
                "Lowercase letters, digits and -.",
                error))
        .of(form -> StaticInstancesPage.publish(form, bind.publish()))
        .button()
        .attrType(EnumTypeButtonType.SUBMIT)
        .attrClass("btn w-fit")
        .text("Bind")
        .__()
        .__();
  }

  private static void kind(Div<?> group, String value, String label, String chosen) {
    group
        .label()
        .attrClass("label gap-3")
        .input()
        .attrType(EnumTypeInputType.RADIO)
        .attrClass("input")
        .attrId("kind-" + value)
        .attrName("kind")
        .attrValue(value)
        .of(input -> {
          if (value.equals(chosen)) {
            input.attrChecked(true);
          }
        })
        .__()
        .text(label)
        .__();
  }

  private static void list(
      Div<?> region,
      List<UnfollowedContainer> containers,
      String dockerProblem,
      RegisterFailure failure) {
    if (dockerProblem != null) {
      region
          .div()
          .attrClass("alert")
          .addAttr("data-variant", "destructive")
          .addAttr("role", "alert")
          .h3()
          .text("The dashboard cannot read Docker")
          .__()
          .section()
          .text(dockerProblem)
          .__()
          .__();
      return;
    }
    if (containers.isEmpty()) {
      region.p().text("Every running container is followed.").__();
      return;
    }
    // A plain container's project is "", so it is a group of its own, after the projects.
    Map<String, List<UnfollowedContainer>> byProject =
        containers.stream()
            .collect(
                Collectors.groupingBy(
                    container ->
                        container.composeProject() == null ? "" : container.composeProject(),
                    LinkedHashMap::new,
                    Collectors.toList()));
    byProject.forEach((project, inProject) -> group(region, project, inProject, failure));
  }

  private static void group(
      Div<?> region,
      String project,
      List<UnfollowedContainer> containers,
      RegisterFailure failure) {
    boolean compose = !project.isEmpty();
    region
        .div()
        .attrClass("flex flex-col gap-3")
        .addAttr("data-compose-project", Layout.attr(project))
        .h2()
        .text(compose ? "Compose project " + project : "Other containers")
        .__()
        .div()
        .attrClass("table-container")
        .table()
        .attrClass("table")
        .thead()
        .tr()
        .of(row -> {
          if (compose) {
            row.th().text("Compose service").__();
          }
        })
        .th()
        .text("Container")
        .__()
        .th()
        .text("Ports")
        .__()
        .th()
        .text("Health")
        .__()
        .th()
        .text("Register")
        .__()
        .__()
        .__()
        .tbody()
        .of(body -> containers.forEach(container -> row(body.tr(), compose, container, failure)))
        .__()
        .__()
        .__()
        .__();
  }

  private static void row(
      Tr<?> row, boolean compose, UnfollowedContainer container, RegisterFailure failure) {
    row.addAttr("data-container", Layout.attr(container.name()));
    if (compose) {
      row.td().attrClass("align-top").text(container.composeService()).__();
    }
    row.td()
        .attrClass("align-top")
        .text(container.name())
        .__()
        .td()
        .attrClass("align-top")
        .of(cell -> ports(cell, container.ports()))
        .__()
        .td()
        .attrClass("align-top")
        .of(cell -> health(cell, container.health()))
        .__()
        .td()
        .attrClass("align-top")
        .of(cell -> registerActions(cell, container, failure))
        .__()
        .__();
  }

  /**
   * One click is enough only when the container has one port, as the port is then known. The choice
   * of name and port is a page of its own, so the 5 s refresh cannot reset what is typed.
   */
  private static void registerActions(
      Td<?> cell, UnfollowedContainer container, RegisterFailure failure) {
    List<Integer> ports = container.containerPorts();
    cell.div()
        .attrClass("flex flex-wrap gap-2")
        .of(actions -> {
          if (ports.size() == 1) {
            actions
                .form()
                .addAttr("method", "post")
                .addAttr("action", Layout.attr(registerPath(container.name())))
                .button()
                .attrType(EnumTypeButtonType.SUBMIT)
                .attrClass("btn")
                .addAttr("data-variant", "outline")
                .addAttr("data-size", "sm")
                .text("Register")
                .__()
                .__();
          }
        })
        .a()
        .attrClass("btn")
        .addAttr("data-variant", ports.size() == 1 ? "ghost" : "outline")
        .addAttr("data-size", "sm")
        .attrHref(Layout.attr(registerPath(container.name())))
        .text(ports.size() == 1 ? "Choose name" : "Choose port")
        .__()
        .__();
    if (failure != null && failure.container().equals(container.name())) {
      cell.div()
          .attrClass("alert mt-2")
          .addAttr("data-variant", "destructive")
          .addAttr("role", "alert")
          .section()
          .text(failure.reason())
          .__()
          .__();
    }
  }

  /**
   * @param name the Service name as typed
   * @param port the container port as typed
   * @param error why the last register failed, shown at its field, or {@code null}
   */
  static String renderRegister(
      Workstation workstation,
      UnfollowedContainer container,
      String name,
      String port,
      InvalidRegistrationException error) {
    return Layout.page(
        "Register " + container.name(),
        workstation.name(),
        Layout.Section.CONTAINERS,
        main ->
            main.h1()
                .text("Register " + container.name())
                .__()
                .p()
                .text(
                    "Binds the container to a Service. A new Service is published with Local"
                        + " Scope.")
                .__()
                .form()
                .attrClass("form flex max-w-md flex-col gap-6")
                .addAttr("method", "post")
                .addAttr("action", Layout.attr(registerPath(container.name())))
                .of(form ->
                    InstanceForm.text(
                        form,
                        "name",
                        "Service name",
                        name,
                        container.service(),
                        "Lowercase letters, digits and -.",
                        error))
                .of(form ->
                    InstanceForm.text(
                        form,
                        "port",
                        "Container port",
                        port,
                        "80",
                        container.containerPorts().isEmpty()
                            ? "The container has no exposed port."
                            : "Its ports: " + container.containerPortList() + ".",
                        error))
                .div()
                .attrClass("flex gap-2")
                .button()
                .attrType(EnumTypeButtonType.SUBMIT)
                .attrClass("btn")
                .text("Register")
                .__()
                .a()
                .attrClass("btn")
                .addAttr("data-variant", "outline")
                .attrHref(PATH)
                .text("Cancel")
                .__()
                .__()
                .__());
  }

  /** The port the register form proposes: the only one, else none. */
  static String proposedPort(UnfollowedContainer container) {
    List<Integer> ports = container.containerPorts();
    return ports.size() == 1 ? String.valueOf(ports.getFirst()) : "";
  }

  private static void ports(Td<?> cell, List<UnfollowedContainer.ContainerPort> ports) {
    if (ports.isEmpty()) {
      cell.text("No exposed port");
      return;
    }
    cell.ul()
        .of(list ->
            ports.forEach(port ->
                list.li()
                    .text(
                        port.hostPort() == null
                            ? port.containerPort() + " not published"
                            : port.containerPort() + " on host port " + port.hostPort())
                    .__()))
        .__();
  }

  private static void health(Td<?> cell, UnfollowedContainer.HealthcheckState health) {
    if (health == null) {
      cell.text("No HEALTHCHECK");
      return;
    }
    String variant =
        switch (health) {
          case HEALTHY -> "success";
          case UNHEALTHY -> "destructive";
          case STARTING -> "secondary";
        };
    cell.span().attrClass("badge").addAttr("data-variant", variant).text(health.value()).__();
  }
}
