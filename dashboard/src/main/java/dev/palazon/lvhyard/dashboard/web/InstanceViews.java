package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.services.CheckResult;
import dev.palazon.lvhyard.dashboard.services.Health;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.Service;
import org.xmlet.htmlapifaster.FlowContent;
import org.xmlet.htmlapifaster.PhrasingContent;
import org.xmlet.htmlapifaster.Td;

final class InstanceViews {

  private InstanceViews() {}

  static void description(Td<?> cell, Service service, Instance instance) {
    cell.div().text(instance.address()).__();
    service.containerBindings().stream()
        .filter(binding -> binding.id().equals(instance.containerBinding()))
        .findFirst()
        .ifPresent(binding ->
            cell.div()
                .attrClass("text-muted-foreground text-sm")
                .text("Bound to " + binding.follows() + ", port " + binding.port() + ".")
                .__());
    if (instance.outOfRange()) {
      cell.div()
          .attrClass("mt-1 flex flex-wrap items-center gap-2")
          .span()
          .attrClass("badge")
          .addAttr("data-variant", "destructive")
          .text("Out of range")
          .__()
          .span()
          .attrClass("text-muted-foreground text-sm")
          .text("Only its own Workstation reaches this address.")
          .__()
          .__();
    }
    if (!instance.tags().isEmpty()) {
      cell.div()
          .attrClass("mt-1 flex flex-wrap gap-1")
          .of(tags ->
              instance
                  .tags()
                  .forEach(tag ->
                      tags.span()
                          .attrClass("badge")
                          .addAttr("data-variant", "outline")
                          .text(tag)
                          .__()))
          .__();
    }
    instance
        .meta()
        .forEach((key, value) ->
            cell.div().attrClass("text-muted-foreground text-sm").text(key + ": " + value).__());
  }

  static void checks(Td<?> cell, Instance instance, String sourceId) {
    boolean hasPassing = false;
    for (CheckResult check : instance.checks()) {
      if (check.health() == Health.PASSING) {
        hasPassing = true;
      } else {
        check(cell, check);
      }
    }
    if (!hasPassing) {
      return;
    }
    cell.custom("details")
        .attrClass("instance-checks")
        .addAttr(
            "data-preserve-open",
            Layout.attr("checks:" + sourceId.length() + ":" + sourceId + instance.id()))
        .custom("summary")
        .text("Passing checks")
        .__()
        .div()
        .of(details -> {
          for (CheckResult check : instance.checks()) {
            if (check.health() == Health.PASSING) {
              check(details, check);
            }
          }
        })
        .__()
        .__();
  }

  private static void check(FlowContent<?, ?> content, CheckResult check) {
    content
        .div()
        .attrClass("instance-check space-y-1")
        .div()
        .attrClass("flex flex-wrap items-center gap-2")
        .span()
        .text(check.name())
        .__()
        .of(div -> badge(div, check.health()))
        .__()
        .of(div -> {
          if (!check.output().isBlank()) {
            div.pre()
                .attrClass("text-muted-foreground text-xs whitespace-pre-wrap")
                .text(check.output().strip())
                .__();
          }
        })
        .__();
  }

  static void health(Td<?> cell, Health health) {
    badge(cell, health);
  }

  private static void badge(PhrasingContent<?, ?> parent, Health health) {
    String variant =
        switch (health) {
          case PASSING -> "success";
          case WARNING -> "risk";
          case CRITICAL -> "destructive";
        };
    parent.span().attrClass("badge").addAttr("data-variant", variant).text(health.value()).__();
  }
}
