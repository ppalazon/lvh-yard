package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;

/** The Catalog: every Service in the Yard. Until Services exist, it only names this Workstation. */
final class CatalogPage {

    private CatalogPage() {
    }

    static String render(Workstation workstation) {
        return Layout.page("Catalog", workstation.name(), main -> main
                .h1().text("Catalog").__()
                .p().text("Workstation " + workstation.name() + ", base domain " + workstation.baseDomain()
                        + ". No Services yet.").__());
    }
}
