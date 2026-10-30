package dev.palazon.lvhyard.dashboard.web;

import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.EnumTypeInputType;
import org.xmlet.htmlapifaster.Main;
import org.xmlet.htmlapifaster.Tr;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.ContainerBinding;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.Scope;
import dev.palazon.lvhyard.dashboard.services.Service;
import dev.palazon.lvhyard.dashboard.services.ServiceWarning;

/**
 * One Service: its state and Publication with buttons to change it and its Scope, its warnings, its Instances, each
 * with its tags, meta and each check's last output, and buttons to edit and remove a Static Instance, and its
 * Container Bindings, with a button to remove each. A Remote Service's page has no forms or buttons: only its Home
 * Workstation can change it.
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

    /** Where the form that removes {@code binding} posts to. */
    static String removePath(Service service, ContainerBinding binding) {
        return path(service) + "/container-bindings/" + binding.id() + "/remove";
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
                    .of(div -> publication(div, workstation, service, own))
                    .of(div -> {
                        if (own) {
                            publicationForms(div, service);
                        }
                    })
                .__()
                .of(content -> {
                    if (!service.warnings().isEmpty()) {
                        warnings(content, service);
                    }
                })
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
                                .of(cell -> InstanceViews.description(cell, service, instance))
                            .__()
                            .td().attrClass("align-top")
                                .of(cell -> InstanceViews.health(cell, instance.health()))
                            .__()
                            .td().attrClass("align-top")
                                .of(cell -> InstanceViews.checks(cell, instance))
                            .__()
                            .of(row -> {
                                if (own && instance.bound()) {
                                    row.td().__();
                                } else if (own) {
                                    actions(row, service, instance);
                                }
                            })
                    .__())).__()
                .__().__()
                .of(content -> {
                    if (!service.containerBindings().isEmpty()) {
                        containerBindings(content, service, own);
                    }
                }));
    }

    /**
     * Each Container Binding, with the Bound Instances it gives now or that its container does not run, and a button
     * to remove it.
     */
    private static void containerBindings(Main<?> content, Service service, boolean own) {
        content.h2().attrClass("text-lg font-semibold").text("Container Bindings").__()
                .div().attrClass("table-container").table().attrClass("table")
                    .thead().tr()
                        .th().text("Container").__()
                        .th().text("Container port").__()
                        .th().text("Bound Instances").__()
                        .of(row -> {
                            if (own) {
                                row.th().of(cell -> cell.span().attrClass("sr-only").text("Actions").__()).__();
                            }
                        })
                    .__().__()
                    .tbody().of(body -> service.containerBindings().forEach(binding -> body.tr()
                            .td().text(binding.container()).__()
                            .td().text(String.valueOf(binding.port())).__()
                            .td().of(cell -> {
                                if (binding.instances().isEmpty()) {
                                    cell.span().attrClass("text-muted-foreground")
                                            .text("No container of this name runs.").__();
                                }
                                service.instances().stream()
                                        .filter(instance -> binding.instances().contains(instance.id()))
                                        .forEach(instance -> cell.div().text(instance.address()).__());
                            }).__()
                            .of(row -> {
                                if (own) {
                                    row.td().div().attrClass("flex justify-end")
                                            .form().addAttr("method", "post")
                                                    .addAttr("action", Layout.attr(removePath(service, binding)))
                                                .button().attrType(EnumTypeButtonType.SUBMIT).attrClass("btn")
                                                        .addAttr("data-variant", "outline")
                                                        .addAttr("data-size", "sm").text("Remove").__()
                                            .__()
                                    .__().__();
                                }
                            })
                    .__())).__()
                .__().__();
    }

    /**
     * The button that publishes the Service with Local Scope, or, once it is published, the buttons that change its
     * Scope and unpublish it.
     */
    private static void publicationForms(Div<?> div, Service service) {
        div.div().attrClass("flex flex-wrap gap-2")
                .of(buttons -> {
                    if (service.published()) {
                        boolean yard = service.publication().scope() == Scope.YARD;
                        buttons.form().addAttr("method", "post")
                                .addAttr("action", Layout.attr(path(service) + "/publish"))
                                .input().attrType(EnumTypeInputType.HIDDEN).attrName("scope")
                                        .attrValue(yard ? Scope.LOCAL.value() : Scope.YARD.value()).__()
                                .button().attrType(EnumTypeButtonType.SUBMIT).attrClass("btn")
                                        .addAttr("data-variant", "outline")
                                        .text(yard ? "Keep on this Workstation" : "Share with the Yard").__()
                        .__();
                    }
                })
                .form().addAttr("method", "post").addAttr("action", Layout.attr(publicationPath(service)))
                    .button().attrType(EnumTypeButtonType.SUBMIT).attrClass("btn")
                            .of(button -> {
                                if (service.published()) {
                                    button.addAttr("data-variant", "outline");
                                }
                            })
                            .text(service.published() ? "Unpublish" : "Publish").__()
                .__()
        .__();
    }

    /** Why other Workstations may not reach the Service, though its Scope gives them a Route. */
    private static void warnings(Main<?> content, Service service) {
        content.div().attrClass("alert").addAttr("data-variant", "destructive").addAttr("role", "alert")
                .h3().text("Other Workstations may not reach this Service").__()
                .section().ul()
                    .of(list -> service.warnings().stream().map(ServiceWarning::message)
                            .forEach(message -> list.li().text(message).__()))
                .__().__()
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
     * hostname here; an own one with Yard Scope also says where the other Workstations reach it.
     */
    private static void publication(Div<?> div, Workstation workstation, Service service, boolean own) {
        if (!service.published()) {
            div.p().text("Not published: it has no hostname. Its Instances stay registered either way.").__();
            return;
        }
        boolean yard = service.publication().scope() == Scope.YARD;
        if (!own && !yard) {
            div.p().text("Published with " + scopeLabel(service) + " Scope: only " + service.workstation()
                    + " routes it.").__();
            return;
        }
        String routedBy;
        if (!yard) {
            routedBy = ": only this Workstation routes it.";
        } else if (own) {
            routedBy = ": every other Workstation routes it at "
                    + service.yardHostname() + ".";
        } else {
            routedBy = ": every Workstation of the Yard routes it.";
        }
        div.p()
                .text("Published at ")
                .of(p -> ServiceViews.hostname(p, service))
                .text(", with " + scopeLabel(service) + " Scope" + routedBy)
        .__();
    }

    private static String scopeLabel(Service service) {
        return switch (service.publication().scope()) {
            case LOCAL -> "Local";
            case YARD -> "Yard";
        };
    }
}
