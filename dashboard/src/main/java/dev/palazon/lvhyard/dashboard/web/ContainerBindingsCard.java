package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.services.ContainerBinding;
import dev.palazon.lvhyard.dashboard.services.Service;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
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

  /** In the header row of the Binding's source, at its right. */
  static void removeButton(Div<?> header, Service service, ContainerBinding binding) {
    header
        .form()
        .attrClass("flex")
        .addAttr("method", "post")
        .addAttr("action", Layout.attr(ServicePage.removePath(service, binding)))
        .button()
        .attrType(EnumTypeButtonType.SUBMIT)
        .attrClass("btn")
        .addAttr("data-variant", "destructive")
        .addAttr("data-size", "sm")
        .addAttr("aria-label", Layout.attr("Remove the Binding to " + binding.follows()))
        .text("Remove")
        .__()
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
