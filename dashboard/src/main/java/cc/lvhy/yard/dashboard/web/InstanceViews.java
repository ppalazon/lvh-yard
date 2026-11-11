package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.services.CheckResult;
import cc.lvhy.yard.dashboard.services.Health;
import cc.lvhy.yard.dashboard.services.Instance;
import cc.lvhy.yard.dashboard.services.Service;
import org.xmlet.htmlapifaster.Dl;
import org.xmlet.htmlapifaster.FlowContent;
import org.xmlet.htmlapifaster.Li;
import org.xmlet.htmlapifaster.PhrasingContent;

final class InstanceViews {

  private InstanceViews() {}

  /**
   * One Instance of an Instance Source: its address and Health, then two columns. The facts as a
   * list of terms and values are on the left, and its checks are on the right.
   */
  static void entry(Li<?> item, Service service, Instance instance) {
    item.div()
        .attrClass("flex flex-wrap items-center gap-2")
        .span()
        .attrClass("font-medium")
        .text(instance.address())
        .__()
        .of(row -> badge(row, instance.health()))
        .of(row -> {
          if (instance.outOfRange()) {
            row.span()
                .attrClass("badge")
                .addAttr("data-variant", "destructive")
                .text("Out of range")
                .__();
          }
        })
        .__();
    if (instance.outOfRange()) {
      item.p()
          .attrClass("text-muted-foreground text-sm")
          .text("Only its own Workstation reaches this address.")
          .__();
    }
    service.containerBindings().stream()
        .filter(binding -> binding.id().equals(instance.containerBinding()))
        .findFirst()
        .ifPresent(binding ->
            item.p()
                .attrClass("text-muted-foreground text-sm")
                .text("Bound to " + binding.follows() + ", port " + binding.port() + ".")
                .__());
    item.div()
        .attrClass("instance-body")
        .of(body -> facts(body, instance))
        .of(body -> checks(body, instance))
        .__();
  }

  /** Only the facts that the Instance has. A Static Instance has no container fields. */
  private static void facts(FlowContent<?, ?> item, Instance instance) {
    boolean any =
        instance.container() != null
            || instance.containerPort() != null
            || instance.bound()
            || !instance.tags().isEmpty()
            || !instance.meta().isEmpty();
    if (!any) {
      return;
    }
    item.dl()
        .attrClass("instance-facts")
        .of(list -> {
          if (instance.container() != null) {
            fact(list, "Container", instance.container());
          }
          if (instance.containerPort() != null) {
            fact(list, "Container Port", instance.containerPort().toString());
          }
          if (instance.bound()) {
            fact(
                list,
                "Host Port",
                instance.hostPort() == null ? "Not published" : instance.hostPort().toString());
          }
          if (!instance.tags().isEmpty()) {
            list.dt()
                .text("Tags")
                .__()
                .dd()
                .attrClass("flex flex-wrap gap-1")
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
          instance.meta().forEach((key, value) -> fact(list, key, value));
        })
        .__();
  }

  private static void fact(Dl<?> list, String term, String value) {
    list.dt().text(term).__().dd().text(value).__();
  }

  /** Every check, passing or not, in one list. */
  private static void checks(FlowContent<?, ?> body, Instance instance) {
    if (instance.checks().isEmpty()) {
      return;
    }
    body.div()
        .attrClass("instance-check-list")
        .p()
        .attrClass("text-muted-foreground text-sm")
        .text("Checks")
        .__()
        .of(list -> instance.checks().forEach(check -> check(list, check)))
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
