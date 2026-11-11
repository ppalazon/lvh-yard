package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.services.ContainerBinding;
import cc.lvhy.yard.dashboard.services.Service;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.Li;

/**
 * The Container Bindings of the Service page. Each Binding is one Instance Source in the ordered
 * list of the Instance Sources card, and its Bound Instances are in the box of that source.
 */
final class ContainerBindingsCard {

  private ContainerBindingsCard() {}

  /** Only a Service of this Workstation gets it. */
  static void openLink(Div<?> row, Service service) {
    row.a()
        .attrClass("btn w-fit")
        .addAttr("data-variant", "outline")
        .addAttr("data-size", "sm")
        .attrHref(Layout.attr(BindContainerPage.path(service)))
        .text("Bind container")
        .__();
  }

  /** Says why a Binding gives no Instances. One that gives some shows them in its box instead. */
  static void noContainer(Li<?> item, ContainerBinding binding) {
    item.p()
        .attrClass("text-muted-foreground")
        .text(
            binding.container() != null
                ? "No container of this name runs."
                : "No container of this Compose service runs.")
        .__();
  }
}
