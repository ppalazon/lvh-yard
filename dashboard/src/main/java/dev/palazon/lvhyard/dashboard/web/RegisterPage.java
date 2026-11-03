package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.ContainerBindingRequest;
import dev.palazon.lvhyard.dashboard.services.InvalidRegistrationException;
import dev.palazon.lvhyard.dashboard.services.Registrations;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.EnumTypeInputType;
import org.xmlet.htmlapifaster.Form;

final class RegisterPage {

  static final String PATH = "/register";

  /** The default {@code kind}. */
  static final String STATIC_INSTANCE = "static";

  static final String CONTAINER_BINDING = "container";

  static final String COMPOSE_BINDING = "compose";

  private RegisterPage() {}

  /**
   * @param kind {@value #STATIC_INSTANCE}, {@value #CONTAINER_BINDING} or {@value #COMPOSE_BINDING}
   */
  record Choice(
      String kind,
      String container,
      String composeProject,
      String composeService,
      String containerPort) {

    static final Choice EMPTY = new Choice(STATIC_INSTANCE, "", "", "", "");

    /** Spring fills the fields by name. A field left out is empty, and the kind is static. */
    Choice {
      kind =
          CONTAINER_BINDING.equals(kind) || COMPOSE_BINDING.equals(kind) ? kind : STATIC_INSTANCE;
      container = container == null ? "" : container;
      composeProject = composeProject == null ? "" : composeProject;
      composeService = composeService == null ? "" : composeService;
      containerPort = containerPort == null ? "" : containerPort;
    }

    boolean containerBinding() {
      return !kind.equals(STATIC_INSTANCE);
    }

    /** The other kind's fields are left out. */
    ContainerBindingRequest request() {
      Integer port = Registrations.parsePort("containerPort", containerPort);
      return kind.equals(COMPOSE_BINDING)
          ? ContainerBindingRequest.compose(composeProject, composeService, port)
          : ContainerBindingRequest.container(container, port);
    }
  }

  /**
   * @param defaultHost the host a Static Instance gets when none is typed
   */
  static String render(
      Workstation workstation,
      String name,
      Choice choice,
      InstanceForm.Fields fields,
      String defaultHost,
      InvalidRegistrationException error) {
    return Layout.page(
        "Register",
        workstation.name(),
        main ->
            main.h1()
                .text("Register a Service")
                .__()
                .p()
                .text(
                    "On Workstation "
                        + workstation.name()
                        + ": a Static Instance at a fixed host and port, which stays registered"
                        + " until you remove it, or a Container Binding, which gives the Service a"
                        + " Bound Instance for each of its containers that runs.")
                .__()
                .form()
                .attrClass("form flex max-w-md flex-col gap-6")
                .addAttr("method", "post")
                .addAttr("action", PATH)
                .of(form ->
                    InstanceForm.text(
                        form,
                        "name",
                        "Service name",
                        name,
                        "grafana",
                        "Lowercase letters, digits and -.",
                        error))
                .of(form -> kinds(form, choice))
                .h2()
                .attrClass("text-lg font-semibold")
                .text("Static Instance")
                .__()
                .of(form -> InstanceForm.fields(form, fields, defaultHost, error))
                .h2()
                .attrClass("text-lg font-semibold")
                .text("Container Binding")
                .__()
                .of(form ->
                    InstanceForm.text(
                        form,
                        "container",
                        "Container name",
                        choice.container(),
                        "grafana",
                        "As docker run --name gives it.",
                        error))
                .of(form ->
                    InstanceForm.text(
                        form,
                        "composeProject",
                        "Compose project",
                        choice.composeProject(),
                        "shop",
                        null,
                        error))
                .of(form ->
                    InstanceForm.text(
                        form,
                        "composeService",
                        "Compose service",
                        choice.composeService(),
                        "web",
                        "Each running container is a Bound Instance.",
                        error))
                .of(form ->
                    InstanceForm.text(
                        form,
                        "containerPort",
                        "Container port",
                        choice.containerPort(),
                        "3000",
                        "The port inside the container.",
                        error))
                .button()
                .attrType(EnumTypeButtonType.SUBMIT)
                .attrClass("btn w-fit")
                .text("Register")
                .__()
                .__());
  }

  private static void kinds(Form<?> form, Choice choice) {
    form.div()
        .attrClass("flex flex-col gap-3")
        .addAttr("role", "radiogroup")
        .addAttr("aria-label", "What to register")
        .of(group -> kind(group, STATIC_INSTANCE, "A Static Instance", choice.kind()))
        .of(group -> kind(group, CONTAINER_BINDING, "A container", choice.kind()))
        .of(group -> kind(group, COMPOSE_BINDING, "A Compose service", choice.kind()))
        .__();
  }

  private static void kind(Div<?> group, String value, String label, String chosen) {
    boolean checked = value.equals(chosen);
    group
        .label()
        .attrClass("label gap-3")
        .input()
        .attrType(EnumTypeInputType.RADIO)
        .attrClass("input")
        .attrName("kind")
        .attrValue(value)
        .of(input -> {
          if (checked) {
            input.attrChecked(true);
          }
        })
        .__()
        .text(label)
        .__();
  }
}
