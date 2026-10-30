package dev.palazon.lvhyard.dashboard.web;

import java.util.List;

import org.xmlet.htmlapifaster.Main;
import org.xmlet.htmlapifaster.Tbody;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.HomeWorkstation;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.Service;
import dev.palazon.lvhyard.dashboard.services.Yard;
import dev.palazon.lvhyard.dashboard.services.YardWorkstation;

/**
 * The Catalog: every Service in the Yard, grouped by Home Workstation, this one first. Each Service has its state and
 * one row per Instance, or one empty row if it has none. A Workstation that does not answer is shown as unreachable.
 */
final class CatalogPage {

    private CatalogPage() {
    }

    static String render(Workstation workstation, Yard yard) {
        return Layout.page("Catalog", workstation.name(), main -> main
                .div().attrClass("flex flex-wrap items-center justify-between gap-4")
                    .h1().text("Catalog").__()
                    .div().attrClass("flex flex-wrap gap-2")
                        .a().attrHref(AliasesPage.PATH).attrClass("btn").addAttr("data-variant", "outline")
                                .text("Aliases").__()
                        .a().attrHref(RegisterPage.PATH).attrClass("btn").text("Register").__()
                    .__()
                .__()
                .p().text("Workstation " + workstation.name() + ", base domain " + workstation.baseDomain() + ".")
                .__()
                .of(content -> home(content, yard.home()))
                .of(content -> yard.remotes().forEach(remote -> remote(content, remote))));
    }

    /** This Workstation's heading, and its Services. */
    private static void home(Main<?> main, HomeWorkstation home) {
        heading(main, home.name(), "secondary", "This Workstation");
        services(main, home.services());
    }

    /** Another Workstation's heading, and its Services, or why they are not known. */
    private static void remote(Main<?> main, YardWorkstation remote) {
        if (remote.unreachable()) {
            heading(main, remote.name(), "destructive", "Workstation unreachable");
            main.p().text("Its Consul datacenter does not answer. Its Services show again by themselves once it"
                    + " does.").__();
            return;
        }
        heading(main, remote.name(), null, null);
        services(main, remote.services());
    }

    /** A Workstation's name as a heading, with a badge beside it when {@code badge} is not null. */
    private static void heading(Main<?> main, String name, String variant, String badge) {
        main.div().attrClass("mt-4 flex flex-wrap items-center gap-3")
                .h2().text(name).__()
                .of(div -> {
                    if (badge != null) {
                        div.span().attrClass("badge").addAttr("data-variant", variant).text(badge).__();
                    }
                })
        .__();
    }

    private static void services(Main<?> main, List<Service> services) {
        if (services.isEmpty()) {
            main.p().text("No Services yet.").__();
            return;
        }
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
