package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.services.Alias;
import cc.lvhy.yard.dashboard.services.AliasTarget;
import cc.lvhy.yard.dashboard.services.ContainerBinding;
import cc.lvhy.yard.dashboard.services.Health;
import cc.lvhy.yard.dashboard.services.Instance;
import cc.lvhy.yard.dashboard.services.InstanceSource;
import cc.lvhy.yard.dashboard.services.Routing;
import cc.lvhy.yard.dashboard.services.Service;
import java.util.List;
import java.util.Optional;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.EnumTypeInputType;
import org.xmlet.htmlapifaster.Li;
import org.xmlet.htmlapifaster.PhrasingContent;

/**
 * The Instance Sources card of the Service page: the Instance Priority switch with the links that
 * add sources, and the ordered list of sources with their Instances.
 */
final class InstanceSourcesCard {

  /** The ID of the card, so that a link can open the Service page at it. */
  static final String ID = "sources";

  private InstanceSourcesCard() {}

  /**
   * The Instances of one source in a box. It starts open when one does not pass, so the problem
   * shows. Its key holds that state, so disclosures.js opens it again when the state changes.
   */
  private static void instances(Li<?> item, Service service, InstanceSource source) {
    List<Instance> given = source.givenInstances();
    if (given.isEmpty()) {
      return;
    }
    long passing = given.stream().filter(instance -> instance.health() == Health.PASSING).count();
    boolean attention = passing < given.size();
    // custom: HtmlFlow's details gives its summary no attributes and its section no children.
    item.custom("details")
        .attrClass("source-instances")
        .addAttr(
            "data-preserve-open",
            Layout.attr(
                "instances:"
                    + source.id().length()
                    + ":"
                    + source.id()
                    + (attention ? ":attention" : ":ok")))
        .of(box -> {
          if (attention) {
            box.addAttr("open", "");
          }
        })
        .custom("summary")
        .text(
            given.size()
                + (given.size() == 1 ? " Instance, " : " Instances, ")
                + passing
                + " passing")
        .__()
        .ul()
        .attrClass("instance-list")
        .of(list ->
            given.forEach(instance ->
                list.li()
                    .attrClass("instance-entry")
                    .of(entry -> InstanceViews.entry(entry, service, instance))
                    .__()))
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
            .attrId(ID)
            .attrClass(ServicePage.CARD + " lg:col-span-2")
            .h2()
            .attrClass("text-lg font-semibold")
            .text("Instance Sources")
            .__();
    card.div()
        .attrClass("flex flex-wrap items-center justify-between gap-x-4 gap-y-3")
        .of(row -> {
          if (own) {
            instancePriority(row, service);
            // Each kind of source adds its link to this group.
            row.div()
                .attrId("add-sources")
                .attrClass("flex flex-wrap gap-2")
                .of(links -> AddStaticInstancePage.openLink(links, service))
                .of(links -> ContainerBindingsCard.openLink(links, service))
                .of(links -> AddAliasPage.openLink(links, service))
                .__();
          } else {
            row.p()
                .attrClass("text-sm text-muted-foreground")
                .addAttr("data-instance-priority", service.instancePriority() ? "on" : "off")
                .text("Instance Priority " + (service.instancePriority() ? "on" : "off"))
                .__();
          }
        })
        .__();
    if (sources.isEmpty()) {
      card.p()
          .text(
              own
                  ? "No Instance Sources yet. Add a Static Instance, bind a container or add an"
                      + " Alias."
                  : "No Instance Sources yet.")
          .__()
          .__();
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
                .attrClass(
                    own
                        ? "flex flex-col gap-2 rounded-lg border p-3 hover:bg-muted"
                            + " focus-within:bg-muted"
                        : "flex flex-col gap-2 rounded-lg border p-3")
                .div()
                .attrClass("flex flex-wrap items-center gap-2")
                .span()
                .attrClass("flex flex-wrap items-center gap-2 font-medium")
                .text((at + 1) + ".")
                .of(name -> title(name, service, source))
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
                  } else if (source.givenInstances().stream()
                      .noneMatch(instance -> instance.health() == Health.PASSING)) {
                    // It gives no Instance, or none passes, so the Route cannot use it now.
                    header
                        .span()
                        .attrClass("badge")
                        .addAttr("data-variant", "destructive")
                        .addAttr("data-unavailable", "")
                        .text("Unavailable")
                        .__();
                  }
                })
                .of(header -> {
                  if (!own) {
                    return;
                  }
                  // The remove button comes last, so it lines up whatever buttons come before.
                  header
                      .div()
                      .attrClass("ml-auto flex flex-wrap items-center justify-end gap-3")
                      .of(group -> {
                        switch (source.kind()) {
                          case STATIC ->
                              source
                                  .givenInstances()
                                  .forEach(instance ->
                                      editLink(group, EditInstancePage.path(service, instance)));
                          case BOUND ->
                              binding(service, source)
                                  .ifPresent(binding ->
                                      editLink(
                                          group, BindContainerPage.editPath(service, binding)));
                          case ALIAS, FOREIGN -> {}
                        }
                        if (movable) {
                          orderSlots(group, service, sources, at, label);
                        }
                        removePath(service, source)
                            .ifPresent(action -> removeButton(group, action, "Remove " + label));
                      })
                      .__();
                })
                .__()
                .of(item -> {
                  switch (source.kind()) {
                    case STATIC -> instances(item, service, source);
                    case BOUND -> boundSource(item, service, source);
                    case ALIAS -> aliasSource(item, service, source, aliases);
                    case FOREIGN -> foreignSource(item, service, source);
                  }
                })
                .__();
          }
        })
        .__()
        .__();
  }

  private static void foreignSource(Li<?> item, Service service, InstanceSource source) {
    item.p()
        .attrClass("text-muted-foreground text-sm")
        .text("Another tool registered it in Consul, so LVH Yard cannot change it.")
        .__();
    instances(item, service, source);
  }

  private static void boundSource(Li<?> item, Service service, InstanceSource source) {
    binding(service, source)
        .ifPresent(binding -> {
          if (source.givenInstances().isEmpty()) {
            ContainerBindingsCard.noContainer(item, binding);
          }
          instances(item, service, source);
        });
  }

  private static Optional<ContainerBinding> binding(Service service, InstanceSource source) {
    return service.containerBindings().stream()
        .filter(binding -> binding.id().equals(source.id()))
        .findFirst();
  }

  /**
   * The Instances of an Alias belong to its Remote Service, so they have no actions here. An Alias
   * that gives no Instances says why.
   */
  private static void aliasSource(
      Li<?> item, Service service, InstanceSource source, List<Alias> aliases) {
    AliasTarget target = AliasTarget.parse(source.id());
    Optional<Alias> alias =
        aliases.stream().filter(candidate -> candidate.id().equals(source.id())).findFirst();
    item.div()
        .attrClass("flex flex-wrap items-center gap-2")
        .a()
        .attrHref(Layout.attr(ServicePage.path(target.workstation(), target.service())))
        .text("Open " + target.label())
        .__()
        .__();
    if (alias.isPresent() && alias.get().reason() != null) {
      item.p()
          .attrClass("text-muted-foreground")
          .addAttr("data-alias-reason", "")
          .text("It gives no Instances now. " + alias.get().reason())
          .__();
    }
    instances(item, service, source);
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

  /**
   * The source as a few parts with badges: its type, what tells it apart, and the port of a
   * Container Binding. The address of a Static or Foreign Instance holds its port already.
   */
  static void title(PhrasingContent<?, ?> parent, Service service, InstanceSource source) {
    switch (source.kind()) {
      case STATIC -> typeAndName(parent, "Static", address(source));
      case FOREIGN -> typeAndName(parent, "Foreign", address(source));
      case ALIAS -> typeAndName(parent, "Alias", AliasTarget.parse(source.id()).label());
      case BOUND ->
          binding(service, source)
              .ifPresentOrElse(
                  binding -> {
                    typeAndName(
                        parent,
                        binding.container() != null ? "Container" : "Compose",
                        binding.container() != null
                            ? binding.container()
                            : binding.composeProject() + " / " + binding.composeService());
                    parent.text(" ");
                    parent
                        .span()
                        .attrClass("badge")
                        .addAttr("data-variant", "outline")
                        .text("Port " + binding.port())
                        .__();
                  },
                  () -> typeAndName(parent, "Binding", source.id()));
    }
  }

  /** The spaces keep the parts apart when someone copies the text. The layout ignores them. */
  private static void typeAndName(PhrasingContent<?, ?> parent, String type, String name) {
    parent.span().attrClass("badge").addAttr("data-variant", "secondary").text(type).__();
    parent.text(" ");
    parent.span().text(name).__();
  }

  /**
   * The source that wins under the Instance Priority, with the number of sources waiting. The
   * Service page shows it with the other facts of a published Service.
   */
  static void routingSummary(Div<?> card, Service service) {
    Routing routing = service.routing();
    if (routing == null) {
      card.p()
          .text(
              service.hostname() == null ? "No Route on this Workstation" : "No Instance in range")
          .__();
      return;
    }
    String routed = null;
    if (routing.offWithPassingInstances()) {
      routed = "Every passing Instance";
    } else if (routing.used() == null) {
      routed = "Every Instance, as none is passing";
    }
    String text = routed;
    card.p()
        .attrClass("flex flex-wrap items-center gap-2")
        .text("Routes to: ")
        .of(line -> {
          if (text != null) {
            line.text(text);
          } else {
            title(line, service, routing.used());
          }
        })
        .__();
    int waiting = service.waitingSources().size();
    if (!routing.off() && waiting > 0) {
      card.p()
          .attrClass("text-muted-foreground")
          .text(waiting + (waiting == 1 ? " source waiting" : " sources waiting"))
          .__();
    }
  }

  private static String address(InstanceSource source) {
    return source.givenInstances().isEmpty()
        ? source.id()
        : source.givenInstances().getFirst().address();
  }

  /** The switch sits in the row of the add links, as both change how the sources work. */
  private static void instancePriority(Div<?> row, Service service) {
    boolean on = service.instancePriority();
    row.div()
        .addAttr("data-instance-priority", on ? "on" : "off")
        .of(box ->
            SwitchForm.render(
                box,
                ServicePage.instancePriorityPath(service),
                "on",
                String.valueOf(!on),
                on,
                "Instance Priority",
                on
                    ? "The Route uses the first source that has a passing Instance."
                    : "The Route shares every passing Instance of every source."))
        .__();
  }

  /**
   * Up and Down sit in two fixed slots at the right of the header, so each is in the same place in
   * every source. The first source has no Up and the last has no Down. Their slots stay empty.
   */
  private static void orderSlots(
      Div<?> group, Service service, List<InstanceSource> sources, int at, String label) {
    group
        .div()
        .attrClass("grid shrink-0 grid-cols-[repeat(2,2rem)] gap-1")
        .of(slots -> {
          if (at > 0) {
            orderForm(
                slots,
                service,
                ServicePage.moved(sources, at, at - 1),
                "Move " + label + " up",
                "m5 12 7-7 7 7",
                "M12 19V5");
          } else {
            slots.span().addAttr("aria-hidden", "true").__();
          }
          if (at < sources.size() - 1) {
            orderForm(
                slots,
                service,
                ServicePage.moved(sources, at, at + 1),
                "Move " + label + " down",
                "M12 5v14",
                "m19 12-7 7-7-7");
          } else {
            slots.span().addAttr("aria-hidden", "true").__();
          }
        })
        .__();
  }

  /** {@code label} is the button's accessible name, as several buttons share an icon. */
  private static void orderForm(
      Div<?> slots,
      Service service,
      List<InstanceSource> order,
      String label,
      String... iconPaths) {
    slots
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
        .addAttr("data-size", "icon-sm")
        .addAttr("aria-label", Layout.attr(label))
        .addAttr("title", Layout.attr(label))
        .of(button -> Layout.icon(button.svg(), "size-4", iconPaths))
        .__()
        .__();
  }

  private static void editLink(Div<?> group, String path) {
    group
        .a()
        .attrClass("btn")
        .addAttr("data-variant", "outline")
        .addAttr("data-size", "sm")
        .attrHref(Layout.attr(path))
        .text("Edit")
        .__();
  }

  /** The page that removes the source, or none when LVH Yard cannot change it. */
  private static Optional<String> removePath(Service service, InstanceSource source) {
    return switch (source.kind()) {
      case STATIC ->
          source.givenInstances().stream()
              .findFirst()
              .map(instance -> ServicePage.removePath(service, instance));
      case BOUND ->
          binding(service, source).map(binding -> ServicePage.removePath(service, binding));
      case ALIAS -> Optional.of(AddAliasPage.removePath(service, source.id()));
      case FOREIGN -> Optional.empty();
    };
  }

  /** An icon button in its own form. The last button of the header, at its right. */
  private static void removeButton(Div<?> group, String action, String label) {
    group
        .form()
        .attrClass("flex")
        .addAttr("method", "post")
        .addAttr("action", Layout.attr(action))
        .button()
        .attrType(EnumTypeButtonType.SUBMIT)
        .attrClass("btn")
        .addAttr("data-variant", "destructive")
        .addAttr("data-size", "icon-sm")
        .addAttr("aria-label", Layout.attr(label))
        .addAttr("title", Layout.attr(label))
        .of(button -> Layout.icon(button.svg(), "size-4", "M18 6 6 18", "m6 6 12 12"))
        .__()
        .__();
  }
}
