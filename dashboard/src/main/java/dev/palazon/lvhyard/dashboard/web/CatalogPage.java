package dev.palazon.lvhyard.dashboard.web;

import java.util.List;

import org.xmlet.htmlapifaster.Main;
import org.xmlet.htmlapifaster.Tbody;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.HomeWorkstation;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.Service;

/**
 * The Catalog: every Service in the Yard, grouped by Home Workstation, this one first. Each Service has its state and
 * one row per Instance, or one empty row if it has none. A Workstation that does not answer is shown as unreachable.
 */
final class CatalogPage {

    private CatalogPage() {
    }

    static String render(Workstation workstation, List<HomeWorkstation> workstations) {
        return Layout.page("Catalog", workstation.name(), main -> main
                .div().attrClass("flex flex-wrap items-center justify-between gap-4")
                    .h1().text("Catalog").__()
                    .a().attrHref(RegisterPage.PATH).attrClass("btn").text("Register").__()
                .__()
                .p().text("Workstation " + workstation.name() + ", base domain " + workstation.baseDomain() + ".")
                .__()
                .of(content -> workstations.forEach(home -> workstation(content, workstation, home))));
    }

    /** A heading for {@code home}, and its Services, or why there are none. */
    private static void workstation(Main<?> main, Workstation workstation, HomeWorkstation home) {
        main.div().attrClass("mt-4 flex flex-wrap items-center gap-3")
                .h2().text(home.name()).__()
                .of(heading -> {
                    if (home.name().equals(workstation.name())) {
                        heading.span().attrClass("badge").addAttr("data-variant", "secondary")
                                .text("This Workstation").__();
                    }
                    if (home.unreachable()) {
                        heading.span().attrClass("badge").addAttr("data-variant", "destructive")
                                .text("Workstation unreachable").__();
                    }
                })
        .__();
        if (home.unreachable()) {
            main.p().text("Its Consul datacenter does not answer. Its Services show again by themselves once it"
                    + " does.").__();
        } else if (home.services().isEmpty()) {
            main.p().text("No Services yet.").__();
        } else {
            services(main, home.services());
        }
    }

    private static void services(Main<?> main, List<Service> services) {
        main.div().attrClass("table-container").table().attrClass("table")
                .thead().tr()
                    .th().text("Service").__()
                    .th().text("State").__()
                    .th().text("Hostname").__()
                    .th().text("Instance").__()
                    .th().text("Health").__()
                .__().__()
                .tbody().of(body -> services.forEach(service -> {
                    if (service.instances().isEmpty()) {
                        row(body, service, null);
                    }
                    service.instances().forEach(instance -> row(body, service, instance));
                })).__()
        .__().__();
    }

    /** A row for {@code instance} of {@code service}, or for the Service alone when {@code instance} is null. */
    private static void row(Tbody<?> body, Service service, Instance instance) {
        body.tr()
                .td().a().attrHref(Layout.attr(ServicePage.path(service))).text(service.name()).__().__()
                .td().of(cell -> ServiceViews.state(cell, service.state())).__()
                .td().of(cell -> ServiceViews.hostname(cell, service)).__()
                .td().text(instance == null ? "No Instances" : instance.address()).__()
                .td().of(cell -> {
                    if (instance != null) {
                        InstanceViews.health(cell, instance.health());
                    }
                }).__()
        .__();
    }
}
