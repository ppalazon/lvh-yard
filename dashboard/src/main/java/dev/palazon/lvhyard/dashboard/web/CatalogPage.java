package dev.palazon.lvhyard.dashboard.web;

import java.util.List;

import org.xmlet.htmlapifaster.Main;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.Service;

/** The Catalog: every Service in the Yard, one row per Instance. */
final class CatalogPage {

    private CatalogPage() {
    }

    static String render(Workstation workstation, List<Service> services) {
        return Layout.page("Catalog", workstation.name(), main -> main
                .div().attrClass("flex flex-wrap items-center justify-between gap-4")
                    .h1().text("Catalog").__()
                    .a().attrHref(RegisterPage.PATH).attrClass("btn").text("Register").__()
                .__()
                .p().text("Workstation " + workstation.name() + ", base domain " + workstation.baseDomain() + ".")
                .__()
                .of(content -> services(content, services)));
    }

    private static void services(Main<?> main, List<Service> services) {
        if (services.isEmpty()) {
            main.p().text("No Services yet.").__();
            return;
        }
        main.div().attrClass("table-container").table().attrClass("table")
                .thead().tr()
                    .th().text("Service").__()
                    .th().text("Workstation").__()
                    .th().text("Instance").__()
                    .th().text("Health").__()
                .__().__()
                .tbody().of(body -> services.forEach(service -> service.instances().forEach(instance -> body.tr()
                        .td().a().attrHref(Layout.attr(ServicePage.path(service))).text(service.name()).__().__()
                        .td().text(service.workstation()).__()
                        .td().text(instance.address()).__()
                        .td().of(cell -> InstanceViews.health(cell, instance.health())).__()
                .__()))).__()
        .__().__();
    }
}
