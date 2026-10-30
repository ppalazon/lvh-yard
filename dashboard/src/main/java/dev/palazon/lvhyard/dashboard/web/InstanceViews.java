package dev.palazon.lvhyard.dashboard.web;

import org.xmlet.htmlapifaster.PhrasingContent;
import org.xmlet.htmlapifaster.Td;

import dev.palazon.lvhyard.dashboard.services.Health;
import dev.palazon.lvhyard.dashboard.services.Instance;

/** How the pages show an Instance: where it is, its tags, meta and Health, and each check's last output. */
final class InstanceViews {

    private InstanceViews() {
    }

    /** Where the Instance is, marked when this Workstation cannot reach it, with its tags and meta below. */
    static void description(Td<?> cell, Instance instance) {
        cell.div().text(instance.address()).__();
        if (instance.unreachable()) {
            cell.div().attrClass("mt-1 flex flex-wrap items-center gap-2")
                    .span().attrClass("badge").addAttr("data-variant", "destructive").text("Unreachable from here").__()
                    .span().attrClass("text-muted-foreground text-sm")
                            .text("On its Workstation's loopback address: no Route here.").__()
            .__();
        }
        if (!instance.tags().isEmpty()) {
            cell.div().attrClass("mt-1 flex flex-wrap gap-1").of(tags -> instance.tags().forEach(tag -> tags
                    .span().attrClass("badge").addAttr("data-variant", "outline").text(tag).__())).__();
        }
        instance.meta().forEach((key, value) -> cell.div().attrClass("text-muted-foreground text-sm")
                .text(key + ": " + value).__());
    }

    /** Each check's name and Health, with what it last saw below. */
    static void checks(Td<?> cell, Instance instance) {
        instance.checks().forEach(check -> cell.div().attrClass("mb-2 flex flex-col gap-1")
                .div().attrClass("flex flex-wrap items-center gap-2")
                    .span().text(check.name()).__()
                    .of(div -> badge(div, check.health()))
                .__()
                .of(div -> {
                    if (!check.output().isBlank()) {
                        div.pre().attrClass("text-muted-foreground max-w-md text-xs break-all whitespace-pre-wrap")
                                .text(check.output().strip()).__();
                    }
                })
        .__());
    }

    /** The Health as a badge: plain when passing, muted when warning, red when critical. */
    static void health(Td<?> cell, Health health) {
        badge(cell, health);
    }

    private static void badge(PhrasingContent<?, ?> parent, Health health) {
        String variant = switch (health) {
            case PASSING -> "primary";
            case WARNING -> "secondary";
            case CRITICAL -> "destructive";
        };
        parent.span().attrClass("badge").addAttr("data-variant", variant).text(health.value()).__();
    }
}
