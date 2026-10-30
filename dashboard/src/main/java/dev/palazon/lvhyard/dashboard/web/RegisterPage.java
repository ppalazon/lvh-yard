package dev.palazon.lvhyard.dashboard.web;

import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.EnumTypeInputType;
import org.xmlet.htmlapifaster.Form;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.ContainerBindingRequest;
import dev.palazon.lvhyard.dashboard.services.InvalidRegistrationException;
import dev.palazon.lvhyard.dashboard.services.Registrations;

/**
 * The Register form, which adds a Static Instance of a Service on this Workstation, or binds the Service to a Docker
 * container by its name or to a Compose service.
 */
final class RegisterPage {

    static final String PATH = "/register";

    /** The {@code kind} that adds a Static Instance, the default. */
    static final String STATIC_INSTANCE = "static";

    /** The {@code kind} that adds a Container Binding to a container by its name. */
    static final String CONTAINER_BINDING = "container";

    /** The {@code kind} that adds a Container Binding to a Compose project and service. */
    static final String COMPOSE_BINDING = "compose";

    private RegisterPage() {
    }

    /**
     * What to register, and the Container Binding's fields, as typed.
     *
     * @param kind {@value #STATIC_INSTANCE}, {@value #CONTAINER_BINDING} or {@value #COMPOSE_BINDING}
     * @param container the name of the container to bind to
     * @param composeProject the Compose project to bind to
     * @param composeService the Compose service to bind to
     * @param containerPort the container port to bind to
     */
    record Choice(String kind, String container, String composeProject, String composeService,
            String containerPort) {

        static final Choice EMPTY = new Choice(STATIC_INSTANCE, "", "", "", "");

        /** A choice as a form posts it, which Spring fills by name: a field left out is empty, the kind static. */
        Choice {
            kind = CONTAINER_BINDING.equals(kind) || COMPOSE_BINDING.equals(kind) ? kind : STATIC_INSTANCE;
            container = container == null ? "" : container;
            composeProject = composeProject == null ? "" : composeProject;
            composeService = composeService == null ? "" : composeService;
            containerPort = containerPort == null ? "" : containerPort;
        }

        /** Whether it adds a Container Binding, to a container by its name or to a Compose service. */
        boolean containerBinding() {
            return !kind.equals(STATIC_INSTANCE);
        }

        /** The Container Binding the fields of its kind ask for; the other kind's fields are left out. */
        ContainerBindingRequest request() {
            Integer port = Registrations.parsePort("containerPort", containerPort);
            return kind.equals(COMPOSE_BINDING) ? ContainerBindingRequest.compose(composeProject, composeService, port)
                    : ContainerBindingRequest.container(container, port);
        }
    }

    /**
     * The form holding the Service's {@code name}, what to register and the fields of both, as typed, with
     * {@code error}, if any, shown at the field it is about.
     *
     * @param defaultHost the host a Static Instance gets when none is typed
     */
    static String render(Workstation workstation, String name, Choice choice, InstanceForm.Fields fields,
            String defaultHost, InvalidRegistrationException error) {
        return Layout.page("Register", workstation.name(), main -> main
                .h1().text("Register a Service").__()
                .p().text("On Workstation " + workstation.name() + ": a Static Instance at a fixed host and port,"
                        + " which stays registered until you remove it, or a Container Binding, which gives the"
                        + " Service a Bound Instance for each of its containers that runs.").__()
                .form().attrClass("form flex max-w-md flex-col gap-6").addAttr("method", "post")
                        .addAttr("action", PATH)
                    .of(form -> InstanceForm.text(form, "name", "Service name", name, "grafana",
                            "Lowercase letters, digits and -.", error))
                    .of(form -> kinds(form, choice))
                    .h2().attrClass("text-lg font-semibold").text("Static Instance").__()
                    .of(form -> InstanceForm.fields(form, fields, defaultHost, error))
                    .h2().attrClass("text-lg font-semibold").text("Container Binding").__()
                    .of(form -> InstanceForm.text(form, "container", "Container name", choice.container(), "grafana",
                            "As docker run --name gives it. Its Bound Instance comes and goes with the container.",
                            error))
                    .of(form -> InstanceForm.text(form, "composeProject", "Compose project", choice.composeProject(),
                            "shop", "Or a Compose project and service, whatever its containers are named.", error))
                    .of(form -> InstanceForm.text(form, "composeService", "Compose service", choice.composeService(),
                            "web", "Each of its containers that runs gives a Bound Instance, so scaling it adds or"
                                    + " removes them.", error))
                    .of(form -> InstanceForm.text(form, "containerPort", "Container port", choice.containerPort(),
                            "3000", "The port inside the container. Published on the host, the Instance is at the"
                                    + " LAN address and the host port; otherwise at the container's own address.",
                            error))
                    .button().attrType(EnumTypeButtonType.SUBMIT).attrClass("btn w-fit").text("Register").__()
                .__());
    }

    /** The choice of what to register, with the fields each one uses. */
    private static void kinds(Form<?> form, Choice choice) {
        form.div().attrClass("flex flex-col gap-3").addAttr("role", "radiogroup")
                .addAttr("aria-label", "What to register")
                .of(group -> kind(group, STATIC_INSTANCE, "A Static Instance: fill in the host, port and check.",
                        choice.kind()))
                .of(group -> kind(group, CONTAINER_BINDING, "A Container Binding to a container: fill in its name"
                        + " and port.", choice.kind()))
                .of(group -> kind(group, COMPOSE_BINDING, "A Container Binding to a Compose service: fill in its"
                        + " project, service and container port.", choice.kind()))
        .__();
    }

    private static void kind(Div<?> group, String value, String label, String chosen) {
        boolean checked = value.equals(chosen);
        group.label().attrClass("label gap-3")
                .input().attrType(EnumTypeInputType.RADIO).attrClass("input").attrName("kind").attrValue(value)
                        .of(input -> {
                            if (checked) {
                                input.attrChecked(true);
                            }
                        }).__()
                .text(label)
        .__();
    }
}
