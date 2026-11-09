package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.services.ContainerBinding;
import dev.palazon.lvhyard.dashboard.services.Health;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.Service;
import java.util.List;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.Td;

/** The Service page's card of Container Bindings, each with its Bound Instances. */
final class ContainerBindingsCard {

  private ContainerBindingsCard() {}

  /**
   * Your own Service that you created always has the card, so you can bind its first container. A
   * Remote Service has it only when it has Bindings.
   */
  static void render(Div<?> content, Service service, boolean own) {
    boolean bindable = own && service.created();
    if (!bindable && service.containerBindings().isEmpty()) {
      return;
    }
    content
        .div()
        .attrId("container-bindings")
        .attrClass("card flex flex-col gap-3 p-4")
        .h2()
        .attrClass("text-lg font-semibold")
        .text("Container Bindings")
        .__()
        .of(card -> {
          if (service.containerBindings().isEmpty()) {
            card.p().text("No container is bound.").__();
          } else {
            table(card, service, own);
          }
          if (bindable) {
            card.a()
                .attrClass("btn w-fit")
                .addAttr("data-variant", "outline")
                .attrHref(Layout.attr(BindContainerPage.path(service)))
                .text("Bind container")
                .__();
          }
        })
        .__();
  }

  private static void table(Div<?> card, Service service, boolean own) {
    card.div()
        .attrClass("table-container")
        .table()
        .attrClass("table")
        .thead()
        .tr()
        .th()
        .text("Follows")
        .__()
        .th()
        .text("Container port")
        .__()
        .th()
        .text("Bound Instances")
        .__()
        .of(row -> {
          if (own) {
            row.th().of(cell -> cell.span().attrClass("sr-only").text("Actions").__()).__();
          }
        })
        .__()
        .__()
        .tbody()
        .of(body ->
            service
                .containerBindings()
                .forEach(binding ->
                    body.tr()
                        .td()
                        .attrClass("align-top")
                        .text(binding.follows())
                        .__()
                        .td()
                        .attrClass("align-top")
                        .text(String.valueOf(binding.port()))
                        .__()
                        .td()
                        .attrClass("align-top")
                        .of(cell -> boundInstances(cell, service, binding))
                        .__()
                        .of(row -> {
                          if (own) {
                            row.td()
                                .attrClass("align-top")
                                .div()
                                .attrClass("flex justify-end")
                                .form()
                                .addAttr("method", "post")
                                .addAttr(
                                    "action", Layout.attr(ServicePage.removePath(service, binding)))
                                .button()
                                .attrType(EnumTypeButtonType.SUBMIT)
                                .attrClass("btn")
                                .addAttr("data-variant", "outline")
                                .addAttr("data-size", "sm")
                                .text("Remove")
                                .__()
                                .__()
                                .__()
                                .__();
                          }
                        })
                        .__()))
        .__()
        .__()
        .__();
  }

  /**
   * A count, then each container that the Binding follows now. The Instances card has the
   * addresses.
   */
  private static void boundInstances(Td<?> cell, Service service, ContainerBinding binding) {
    List<Instance> bound =
        service.instances().stream()
            .filter(instance -> binding.instances().contains(instance.id()))
            .toList();
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
