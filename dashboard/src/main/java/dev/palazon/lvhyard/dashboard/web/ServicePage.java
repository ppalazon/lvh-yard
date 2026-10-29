package dev.palazon.lvhyard.dashboard.web;

import org.xmlet.htmlapifaster.EnumTypeButtonType;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.Service;

/** One Service: its Instances, their Health, and a button to remove each. */
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

    static String render(Workstation workstation, Service service) {
        return Layout.page(service.name(), workstation.name(), main -> main
                .h1().text(service.name()).__()
                .p().text("On Workstation " + service.workstation() + ".").__()
                .div().attrClass("table-container").table().attrClass("table")
                    .thead().tr()
                        .th().text("Instance").__()
                        .th().text("Health").__()
                        .th().of(cell -> cell.span().attrClass("sr-only").text("Actions").__()).__()
                    .__().__()
                    .tbody().of(body -> service.instances().forEach(instance -> body.tr()
                            .td().text(instance.address()).__()
                            .td().of(cell -> InstanceViews.health(cell, instance.health())).__()
                            .td().attrClass("text-right")
                                .form().addAttr("method", "post")
                                        .addAttr("action", Layout.attr(removePath(service, instance)))
                                    .button().attrType(EnumTypeButtonType.SUBMIT).attrClass("btn")
                                            .addAttr("data-variant", "outline").addAttr("data-size", "sm")
                                            .text("Remove").__()
                                .__()
                            .__()
                    .__())).__()
                .__().__());
    }
}
