package dev.palazon.lvhyard.dashboard.web;

import org.xmlet.htmlapifaster.EnumTypeButtonType;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.InvalidRegistrationException;

/** The Register form, which adds a Static Instance of a Service on this Workstation. */
final class RegisterPage {

    static final String PATH = "/register";

    private RegisterPage() {
    }

    /**
     * The form holding the Service's {@code name} and the Instance's {@code fields}, as typed, with {@code error},
     * if any, shown at the field it is about.
     *
     * @param defaultHost the host an Instance gets when none is typed
     */
    static String render(Workstation workstation, String name, InstanceForm.Fields fields, String defaultHost,
            InvalidRegistrationException error) {
        return Layout.page("Register", workstation.name(), main -> main
                .h1().text("Register a Static Instance").__()
                .p().text("A Service at a fixed host and port on Workstation " + workstation.name()
                        + ". It stays registered until you remove it.").__()
                .form().attrClass("form flex max-w-md flex-col gap-6").addAttr("method", "post")
                        .addAttr("action", PATH)
                    .of(form -> InstanceForm.text(form, "name", "Service name", name, "grafana",
                            "Lowercase letters, digits and -.", error))
                    .of(form -> InstanceForm.fields(form, fields, defaultHost, error))
                    .button().attrType(EnumTypeButtonType.SUBMIT).attrClass("btn w-fit").text("Register").__()
                .__());
    }
}
