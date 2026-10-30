package dev.palazon.lvhyard.dashboard.web;

import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.Tr;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.Service;

/**
 * One Service: its state and Publication with a button to change it, and its Instances, each with its tags, meta and
 * each check's last output, and buttons to edit and remove it. A Remote Service's page has no forms or buttons: only
 * its Home Workstation can change it.
 */
final class ServicePage {

    private ServicePage() {
    }

    /** Where a Service's page is: {@code /services/<workstation>/<name>}. */
    static String path(Service service) {
        return path(service.workstation(), service.name());
    }

    static String path(String workstation, String name) {
        return "/services/" + workstation + "/" + name;
    }

    /** Where the form that removes {@code instance} posts to. */
    static String removePath(Service service, Instance instance) {
        return path(service) + "/instances/" + instance.id() + "/remove";
    }

    /** Where the form that publishes the Service, or unpublishes it, posts to. */
    static String publicationPath(Service service) {
        return path(service) + (service.published() ? "/unpublish" : "/publish");
    }

    static String render(Workstation workstation, Service service) {
        boolean own = workstation.is(service.workstation());
        return Layout.page(service.name(), workstation.name(), main -> main
                .div().attrClass("flex flex-wrap items-center gap-3")
                    .h1().text(service.name()).__()
                    .of(heading -> ServiceViews.state(heading, service.state()))
                .__()
                .p().text("On Workstation " + service.workstation() + "."
                        + (own ? "" : " It can be changed only on " + service.workstation() + ".")).__()
                .div().attrClass("flex flex-wrap items-center justify-between gap-4")
                    .of(div -> publication(div, service, own))
                    .of(div -> {
                        if (own) {
                            publicationForm(div, service);
                        }
                    })
                .__()
                .of(content -> {
                    if (service.instances().isEmpty()) {
                        content.p().text("No Instances are registered.").__();
                    }
                })
                .div().attrClass("table-container").table().attrClass("table")
                    .thead().tr()
                        .th().text("Instance").__()
                        .th().text("Health").__()
                        .th().text("Checks").__()
                        .of(row -> {
                            if (own) {
                                row.th().of(cell -> cell.span().attrClass("sr-only").text("Actions").__()).__();
                            }
                        })
                    .__().__()
                    .tbody().of(body -> service.instances().forEach(instance -> body.tr()
                            .td().attrClass("align-top")
                                .of(cell -> InstanceViews.description(cell, instance))
                            .__()
                            .td().attrClass("align-top")
                                .of(cell -> InstanceViews.health(cell, instance.health()))
                            .__()
                            .td().attrClass("align-top")
                                .of(cell -> InstanceViews.checks(cell, instance))
                            .__()
                            .of(row -> {
                                if (own) {
                                    actions(row, service, instance);
                                }
                            })
                    .__())).__()
                .__().__());
    }

    /** The button that publishes the Service, or unpublishes it. */
    private static void publicationForm(Div<?> div, Service service) {
        div.form().addAttr("method", "post").addAttr("action", Layout.attr(publicationPath(service)))
                .button().attrType(EnumTypeButtonType.SUBMIT).attrClass("btn")
                        .of(button -> {
                            if (service.published()) {
                                button.addAttr("data-variant", "outline");
                            }
                        })
                        .text(service.published() ? "Unpublish" : "Publish").__()
        .__();
    }

    /** The buttons that edit and remove {@code instance}. */
    private static void actions(Tr<?> row, Service service, Instance instance) {
        row.td().attrClass("align-top")
                .div().attrClass("flex justify-end gap-2")
                    .a().attrClass("btn").addAttr("data-variant", "outline").addAttr("data-size", "sm")
                            .attrHref(Layout.attr(EditInstancePage.path(service, instance)))
                            .text("Edit").__()
                    .form().addAttr("method", "post").addAttr("action", Layout.attr(removePath(service, instance)))
                        .button().attrType(EnumTypeButtonType.SUBMIT).attrClass("btn")
                                .addAttr("data-variant", "outline").addAttr("data-size", "sm")
                                .text("Remove").__()
                    .__()
                .__()
        .__();
    }

    /**
     * Where the Service is published, with its Scope, or that it is not. A Remote Service with Local Scope has no
     * hostname here.
     */
    private static void publication(Div<?> div, Service service, boolean own) {
        if (!service.published()) {
            div.p().text("Not published: it has no hostname. Its Instances stay registered either way.").__();
            return;
        }
        if (!own) {
            div.p().text("Published with " + scopeLabel(service) + " Scope: only " + service.workstation()
                    + " routes it.").__();
            return;
        }
        div.p()
                .text("Published at ")
                .of(p -> ServiceViews.hostname(p, service))
                .text(", with " + scopeLabel(service) + " Scope: only this Workstation routes it.")
        .__();
    }

    private static String scopeLabel(Service service) {
        return switch (service.publication().scope()) {
            case LOCAL -> "Local";
        };
    }
}
