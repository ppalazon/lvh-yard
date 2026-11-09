package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.services.Alias;
import dev.palazon.lvhyard.dashboard.services.AliasTarget;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.InstanceSource;
import dev.palazon.lvhyard.dashboard.services.Routing;
import dev.palazon.lvhyard.dashboard.services.Service;
import java.util.List;
import java.util.Optional;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.EnumTypeInputType;
import org.xmlet.htmlapifaster.Li;
import org.xmlet.htmlapifaster.Tr;

/**
 * The Instance Sources card of the Service page: the Instance Priority toggle, the links that add
 * sources, and the ordered list of sources with their Instances.
 */
final class InstanceSourcesCard {

  private InstanceSourcesCard() {}

  /** {@code own} false leaves out the actions, such as for the Instances of an Alias. */
  private static void instanceTable(
      Li<?> item, Service service, List<Instance> instances, boolean own) {
    item.div()
        .attrClass("table-container")
        .table()
        .attrClass("table")
        .thead()
        .tr()
        .th()
        .text("Instance")
        .__()
        .th()
        .text("Container")
        .__()
        .th()
        .text("Container Port")
        .__()
        .th()
        .text("Host Port")
        .__()
        .th()
        .text("Health")
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
            instances.forEach(instance ->
                body.tr()
                    .td()
                    .attrClass("align-top")
                    .of(cell -> InstanceViews.description(cell, service, instance))
                    .__()
                    .td()
                    .text(instance.container() == null ? "" : instance.container())
                    .__()
                    .td()
                    .text(
                        instance.containerPort() == null ? "" : instance.containerPort().toString())
                    .__()
                    .td()
                    .text(
                        !instance.bound()
                            ? ""
                            : instance.hostPort() == null
                                ? "Not published"
                                : instance.hostPort().toString())
                    .__()
                    .td()
                    .attrClass("align-top")
                    .of(cell -> InstanceViews.health(cell, instance.health()))
                    .of(cell -> InstanceViews.checks(cell, instance))
                    .__()
                    .of(row -> {
                      if (own && !instance.staticInstance()) {
                        row.td().__();
                      } else if (own) {
                        actions(row, service, instance);
                      }
                    })
                    .__()))
        .__()
        .__()
        .__();
  }

  /**
   * One ordered list of the Instance Sources, each with its Instances. Each up or down button posts
   * the whole new order, so the page needs no script. The buttons hide while the Instance Priority
   * is off, but the order stays. Each kind of source has its own method below.
   */
  static void render(Div<?> grid, Service service, List<Alias> aliases, boolean own) {
    List<InstanceSource> sources = service.sources();
    boolean movable = own && service.instancePriority() && sources.size() > 1;
    Routing routing = service.routing();
    Div<?> card =
        grid.div()
            .attrId("sources")
            .attrClass(ServicePage.CARD + " lg:col-span-2")
            .h2()
            .attrClass("text-lg font-semibold")
            .text("Instance Sources")
            .__();
    card.div()
        .attrClass("flex flex-wrap items-center justify-between gap-2")
        .p()
        .attrClass("text-muted-foreground")
        .addAttr("data-instance-priority", service.instancePriority() ? "on" : "off")
        .text(
            service.instancePriority()
                ? "Instance Priority on: the Route uses the first source with a passing Instance."
                : "Instance Priority off: every passing Instance of every source shares the"
                    + " Route.")
        .__()
        .of(row -> {
          if (own) {
            instancePriorityForm(row, service);
          }
        })
        .__();
    if (own) {
      // Each kind of source adds its link or form to this row.
      card.div()
          .attrId("add-sources")
          .attrClass("flex flex-wrap gap-2")
          .of(row -> AddStaticInstancePage.openLink(row, service))
          .of(row -> ContainerBindingsCard.openLink(row, service))
          .of(row -> AddAliasPage.openLink(row, service))
          .__();
    }
    if (sources.isEmpty()) {
      card.p().text("No Instance Sources yet.").__().__();
      return;
    }
    card.ol()
        .attrClass("flex flex-col gap-4")
        .of(list -> {
          for (int i = 0; i < sources.size(); i++) {
            InstanceSource source = sources.get(i);
            int at = i;
            String label = label(service, source);
            boolean inUse = routing != null && source.equals(routing.used());
            list.li()
                .addAttr("data-source", Layout.attr(source.id()))
                .attrClass("flex flex-col gap-2")
                .div()
                .attrClass("flex flex-wrap items-center gap-2")
                .span()
                .attrClass("font-medium")
                .text((at + 1) + ". " + label)
                .__()
                .of(header -> {
                  if (inUse) {
                    header
                        .span()
                        .attrClass("badge")
                        .addAttr("data-variant", "success")
                        .addAttr("data-in-use", "")
                        .text("In use")
                        .__();
                  }
                  if (movable && at > 0) {
                    orderForm(
                        header,
                        service,
                        ServicePage.moved(sources, at, at - 1),
                        "Up",
                        "Move " + label + " up");
                  }
                  if (movable && at < sources.size() - 1) {
                    orderForm(
                        header,
                        service,
                        ServicePage.moved(sources, at, at + 1),
                        "Down",
                        "Move " + label + " down");
                  }
                })
                .__()
                .of(item -> {
                  switch (source.kind()) {
                    case STATIC -> staticSource(item, service, source, own);
                    case BOUND -> boundSource(item, service, source, own);
                    case ALIAS -> aliasSource(item, service, source, aliases, own);
                    case FOREIGN -> foreignSource(item, service, source, own);
                  }
                })
                .__();
          }
        })
        .__()
        .__();
  }

  private static void staticSource(
      Li<?> item, Service service, InstanceSource source, boolean own) {
    instanceTable(item, service, source.givenInstances(), own);
  }

  private static void foreignSource(
      Li<?> item, Service service, InstanceSource source, boolean own) {
    item.p()
        .attrClass("text-muted-foreground text-sm")
        .text("Another tool registered it in Consul, so LVH Yard cannot change it.")
        .__();
    instanceTable(item, service, source.givenInstances(), own);
  }

  private static void boundSource(Li<?> item, Service service, InstanceSource source, boolean own) {
    service.containerBindings().stream()
        .filter(binding -> binding.id().equals(source.id()))
        .findFirst()
        .ifPresent(binding -> {
          ContainerBindingsCard.source(item, service, binding, source.givenInstances(), own);
          if (!source.givenInstances().isEmpty()) {
            instanceTable(item, service, source.givenInstances(), own);
          }
        });
  }

  /**
   * The Instances of an Alias belong to its Remote Service, so they have no actions here. An Alias
   * that gives no Instances says why.
   */
  private static void aliasSource(
      Li<?> item, Service service, InstanceSource source, List<Alias> aliases, boolean own) {
    AliasTarget target = AliasTarget.parse(source.id());
    Optional<Alias> alias =
        aliases.stream().filter(candidate -> candidate.id().equals(source.id())).findFirst();
    item.div()
        .attrClass("flex flex-wrap items-center gap-2")
        .a()
        .attrHref(Layout.attr(ServicePage.path(target.workstation(), target.service())))
        .text("Open " + target.label())
        .__()
        .of(row -> {
          if (own) {
            row.form()
                .addAttr("method", "post")
                .addAttr("action", Layout.attr(AddAliasPage.removePath(service, source.id())))
                .button()
                .attrType(EnumTypeButtonType.SUBMIT)
                .attrClass("btn")
                .addAttr("data-variant", "outline")
                .addAttr("data-size", "sm")
                .addAttr("aria-label", Layout.attr("Remove " + label(service, source)))
                .text("Remove")
                .__()
                .__();
          }
        })
        .__();
    if (alias.isPresent() && alias.get().reason() != null) {
      item.p()
          .attrClass("text-muted-foreground")
          .addAttr("data-alias-reason", "")
          .text("It gives no Instances now. " + alias.get().reason())
          .__();
    }
    if (!source.givenInstances().isEmpty()) {
      instanceTable(item, service, source.givenInstances(), false);
    }
  }

  /** How the page and the summary name a source, such as {@code Static Instance 10.0.0.5:80}. */
  static String label(Service service, InstanceSource source) {
    String detail =
        switch (source.kind()) {
          case STATIC, FOREIGN -> address(source);
          case BOUND ->
              service.containerBindings().stream()
                  .filter(binding -> binding.id().equals(source.id()))
                  .findFirst()
                  .map(binding -> "to " + binding.follows() + ", port " + binding.port())
                  .orElse(source.id());
          case ALIAS -> AliasTarget.parse(source.id()).label();
        };
    return source.kind().describe(detail);
  }

  private static String address(InstanceSource source) {
    return source.givenInstances().isEmpty()
        ? source.id()
        : source.givenInstances().getFirst().address();
  }

  private static void instancePriorityForm(Div<?> row, Service service) {
    boolean on = service.instancePriority();
    row.form()
        .addAttr("method", "post")
        .addAttr("action", Layout.attr(ServicePage.instancePriorityPath(service)))
        .input()
        .attrType(EnumTypeInputType.HIDDEN)
        .attrName("on")
        .attrValue(String.valueOf(!on))
        .__()
        .button()
        .attrType(EnumTypeButtonType.SUBMIT)
        .attrClass("btn")
        .addAttr("data-variant", "outline")
        .addAttr("data-size", "sm")
        .text(on ? "Turn off Instance Priority" : "Turn on Instance Priority")
        .__()
        .__();
  }

  /** {@code label} is the button's accessible name, as several buttons share a short text. */
  private static void orderForm(
      Div<?> buttons, Service service, List<InstanceSource> order, String text, String label) {
    buttons
        .form()
        .addAttr("method", "post")
        .addAttr("action", Layout.attr(ServicePage.instancePriorityPath(service)))
        .input()
        .attrType(EnumTypeInputType.HIDDEN)
        .attrName("order")
        .attrValue(Layout.attr(String.join(",", order.stream().map(InstanceSource::id).toList())))
        .__()
        .button()
        .attrType(EnumTypeButtonType.SUBMIT)
        .attrClass("btn")
        .addAttr("data-variant", "outline")
        .addAttr("data-size", "sm")
        .addAttr("aria-label", Layout.attr(label))
        .text(text)
        .__()
        .__();
  }

  private static void actions(Tr<?> row, Service service, Instance instance) {
    row.td()
        .attrClass("align-top")
        .div()
        .attrClass("flex justify-end gap-2")
        .a()
        .attrClass("btn")
        .addAttr("data-variant", "outline")
        .addAttr("data-size", "sm")
        .attrHref(Layout.attr(EditInstancePage.path(service, instance)))
        .text("Edit")
        .__()
        .form()
        .addAttr("method", "post")
        .addAttr("action", Layout.attr(ServicePage.removePath(service, instance)))
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
}
