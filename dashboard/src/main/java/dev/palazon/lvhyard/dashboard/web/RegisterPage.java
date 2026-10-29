package dev.palazon.lvhyard.dashboard.web;

import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.EnumTypeInputType;
import org.xmlet.htmlapifaster.Form;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.InvalidRegistrationException;

/** The Register form, which adds a Static Instance of a Service on this Workstation. */
final class RegisterPage {

    static final String PATH = "/register";

    private RegisterPage() {
    }

    /**
     * What was typed into the form, as typed.
     *
     * @param name the Service's name
     * @param host the Instance's host; empty for the default host
     * @param port the Instance's port
     */
    record Fields(String name, String host, String port) {

        static final Fields EMPTY = new Fields("", "", "");
    }

    /**
     * The form holding {@code fields}, with {@code error}, if any, shown at the field it is about.
     *
     * @param defaultHost the host an Instance gets when none is typed
     */
    static String render(Workstation workstation, Fields fields, String defaultHost,
            InvalidRegistrationException error) {
        return Layout.page("Register", workstation.name(), main -> main
                .h1().text("Register a Static Instance").__()
                .p().text("A Service at a fixed host and port on Workstation " + workstation.name()
                        + ". It stays registered until you remove it.").__()
                .form().attrClass("form flex max-w-md flex-col gap-6").addAttr("method", "post")
                        .addAttr("action", PATH)
                    .of(form -> field(form, "name", "Service name", fields.name(), "grafana",
                            "Lowercase letters, digits and -.", error))
                    .of(form -> field(form, "host", "Host", fields.host(), defaultHost,
                            "Empty: this Workstation's LAN address, " + defaultHost + ".", error))
                    .of(form -> field(form, "port", "Port", fields.port(), "3000", null, error))
                    .button().attrType(EnumTypeButtonType.SUBMIT).attrClass("btn w-fit").text("Register").__()
                .__());
    }

    private static void field(Form<?> form, String name, String label, String value, String placeholder,
            String help, InvalidRegistrationException error) {
        boolean invalid = error != null && error.field().equals(name);
        form.div().attrClass("field").of(field -> {
                    if (invalid) {
                        field.addAttr("data-invalid", "true");
                    }
                })
                .label().attrFor(name).text(label).__()
                .input().attrType(EnumTypeInputType.TEXT).attrClass("input").attrId(name).attrName(name)
                        .attrValue(Layout.attr(value)).attrPlaceholder(Layout.attr(placeholder))
                        .of(input -> {
                            if (invalid) {
                                input.addAttr("aria-invalid", "true").addAttr("aria-describedby", name + "-error");
                            }
                        }).__()
                .of(field -> {
                    if (invalid) {
                        field.p().attrId(name + "-error").addAttr("role", "alert").text(error.getMessage()).__();
                    } else if (help != null) {
                        field.p().attrClass("text-muted-foreground text-sm").text(help).__();
                    }
                })
        .__();
    }
}
