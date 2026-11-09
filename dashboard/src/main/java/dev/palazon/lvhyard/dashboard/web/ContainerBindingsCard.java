package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.services.ContainerBinding;
import dev.palazon.lvhyard.dashboard.services.Health;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.Service;
import java.util.List;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.Li;

/**
 * The Container Bindings of the Service page. Each Binding is one Instance Source in the ordered
 * list of the Instance Sources card.
 */
final class ContainerBindingsCard {

  private ContainerBindingsCard() {}

  /** Only your own Service that you created gets it. */
  static void openLink(Div<?> row, Service service) {
    row.a()
        .attrClass("btn w-fit")
        .addAttr("data-variant", "outline")
        .attrHref(Layout.attr(BindContainerPage.path(service)))
        .text("Bind container")
        .__();
  }

  /** The body of one Binding's item in the ordered list. */
  static void source(
      Li<?> item, Service service, ContainerBinding binding, List<Instance> bound, boolean own) {
    item.div()
        .attrId(Layout.attr("container-binding-" + binding.id()))
        .attrClass("flex flex-wrap items-start justify-between gap-2")
        .div()
        .of(div -> boundInstances(div, binding, bound))
        .__()
        .of(div -> {
          if (own) {
            div.form()
                .addAttr("method", "post")
                .addAttr("action", Layout.attr(ServicePage.removePath(service, binding)))
                .button()
                .attrType(EnumTypeButtonType.SUBMIT)
                .attrClass("btn")
                .addAttr("data-variant", "outline")
                .addAttr("data-size", "sm")
                .addAttr("aria-label", Layout.attr("Remove the Binding to " + binding.follows()))
                .text("Remove")
                .__()
                .__();
          }
        })
        .__();
  }

  /** A count, then each container that the Binding follows now. */
  private static void boundInstances(Div<?> cell, ContainerBinding binding, List<Instance> bound) {
    if (bound.isEmpty()) {
      cell.span()
          .attrClass("text-muted-foreground")
          .text(
              binding.container() != null
                  ? "No container of this name runs."
                  : "No container of this Compose service runs.")
          .__();
      return;
    }
    long passing = bound.stream().filter(instance -> instance.health() == Health.PASSING).count();
    cell.div()
        .text(
            bound.size()
                + (bound.size() == 1 ? " container, " : " containers, ")
                + passing
                + " passing")
        .__()
        .ul()
        .attrClass("text-muted-foreground flex flex-col gap-1 text-sm")
        .of(list ->
            bound.forEach(instance ->
                list.li()
                    .text(
                        (instance.container() == null ? instance.address() : instance.container())
                            + (instance.health() == Health.PASSING ? ", passing" : ", not passing"))
                    .__()))
        .__();
  }
}
