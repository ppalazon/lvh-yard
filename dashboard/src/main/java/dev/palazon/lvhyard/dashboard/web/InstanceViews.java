package dev.palazon.lvhyard.dashboard.web;

import org.xmlet.htmlapifaster.Td;

import dev.palazon.lvhyard.dashboard.services.Health;

/** How the pages show an Instance's Health. */
final class InstanceViews {

    private InstanceViews() {
    }

    /** The Health as a badge: plain when passing, muted when warning, red when critical. */
    static void health(Td<?> cell, Health health) {
        String variant = switch (health) {
            case PASSING -> "primary";
            case WARNING -> "secondary";
            case CRITICAL -> "destructive";
        };
        cell.span().attrClass("badge").addAttr("data-variant", variant).text(health.value()).__();
    }
}
