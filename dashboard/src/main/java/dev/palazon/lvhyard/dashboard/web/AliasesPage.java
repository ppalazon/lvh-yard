package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.Alias;
import dev.palazon.lvhyard.dashboard.services.InvalidRegistrationException;
import dev.palazon.lvhyard.dashboard.services.Service;
import dev.palazon.lvhyard.dashboard.services.Yard;
import java.util.List;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.FlowContent;
import org.xmlet.htmlapifaster.Main;
import org.xmlet.htmlapifaster.Td;
import org.xmlet.htmlapifaster.Tr;

final class AliasesPage {

  static final String PATH = "/aliases";

  static final String NEW_PATH = PATH + "/new";

  private static final FormPage.Spec ADD =
      new FormPage.Spec(Layout.Section.ALIASES, "Add an Alias", null, NEW_PATH, PATH, "Add");

  private AliasesPage() {}

  static String removePath(Alias alias) {
    return PATH + "/" + alias.name() + "/remove";
  }

  /** The Remote Services that an Alias can name: those with Yard Scope. */
  static List<Service> shared(Yard yard) {
    return yard.remotes().stream()
        .flatMap(remote -> remote.services().stream())
        .filter(Service::sharedWithYard)
        .toList();
  }

  static String render(Workstation workstation, Yard yard, List<Alias> aliases) {
    boolean none = shared(yard).isEmpty();
    return Layout.page(
        "Aliases",
        workstation.name(),
        Layout.Section.ALIASES,
        main ->
            main.h1()
                .text("Aliases")
                .__()
                .p()
                .text(
                    "Short hostnames for Remote Services, at <alias>."
                        + workstation.baseDomain()
                        + ".")
                .__()
                .of(content -> {
                  if (none) {
                    content
                        .p()
                        .text(
                            "No Remote Service is shared with the Yard yet: an Alias needs one"
                                + " with Yard Scope.")
                        .__();
                  } else {
                    FormPage.openLink(content, ADD);
                  }
                })
                .of(content -> list(content, aliases)));
  }

  /**
   * @param shared the Remote Services the form offers, from {@link #shared}
   * @param target the chosen Remote Service, as {@code <workstation>/<service>}
   * @param error why the last post failed, shown at its field, or {@code null}
   */
  static String renderNew(
      Workstation workstation,
      List<Service> shared,
      String name,
      String target,
      InvalidRegistrationException error) {
    return FormPage.render(
        workstation, ADD, form -> {}, section -> fields(section, shared, name, target, error));
  }

  private static void list(Main<?> main, List<Alias> aliases) {
    if (aliases.isEmpty()) {
      main.p().text("No Aliases yet.").__();
      return;
    }
    main.div()
        .attrClass("table-container")
        .table()
        .attrClass("table")
        .thead()
        .tr()
        .th()
        .text("Alias")
        .__()
        .th()
        .text("Remote Service")
        .__()
        .th()
        .text("State")
        .__()
        .th()
        .of(cell -> cell.span().attrClass("sr-only").text("Actions").__())
        .__()
        .__()
        .__()
        .tbody()
        .of(body ->
            aliases.forEach(alias ->
                body.tr()
                    .td()
                    .attrClass("align-top")
                    .of(cell -> Layout.externalLink(cell, alias.hostname()))
                    .__()
                    .td()
                    .attrClass("align-top")
                    .a()
                    .attrHref(Layout.attr(ServicePage.path(alias.workstation(), alias.service())))
                    .text(alias.service() + " on " + alias.workstation())
                    .__()
                    .__()
                    .td()
                    .attrClass("align-top")
                    .of(cell -> state(cell, alias))
                    .__()
                    .of(row -> remove(row, alias))
                    .__()))
        .__()
        .__()
        .__();
  }

  private static void state(Td<?> cell, Alias alias) {
    String label =
        switch (alias.state()) {
          case ACTIVE -> "Active";
          case FALLBACK -> "Fallback";
          case NOT_SHARED -> "Not shared";
          case UNAVAILABLE -> "Unavailable";
          case UNREACHABLE -> "Workstation unreachable";
          case MISSING -> "Missing";
        };
    cell.span()
        .attrClass("badge")
        .addAttr("data-variant", alias.active() ? "success" : "outline")
        .text(label)
        .__();
    if (alias.reason() != null) {
      cell.p().attrClass("text-muted-foreground text-sm").text(alias.reason()).__();
    }
  }

  private static void remove(Tr<?> row, Alias alias) {
    row.td()
        .attrClass("align-top")
        .div()
        .attrClass("flex justify-end")
        .form()
        .addAttr("method", "post")
        .addAttr("action", Layout.attr(removePath(alias)))
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

  private static void fields(
      FlowContent<?, ?> section,
      List<Service> shared,
      String name,
      String target,
      InvalidRegistrationException error) {
    InstanceForm.text(
        section, "name", "Alias", name, "grafana", "Lowercase letters, digits and -.", error);
    section
        .div()
        .attrClass("field")
        .label()
        .attrFor("target")
        .text("Remote Service")
        .__()
        .select()
        .attrClass("select")
        .attrId("target")
        .attrName("target")
        .of(select ->
            shared.forEach(service -> {
              String value = service.workstation() + "/" + service.name();
              select
                  .option()
                  .attrValue(Layout.attr(value))
                  .of(option -> option.attrSelected(value.equals(target)))
                  .text(service.name() + " on " + service.workstation())
                  .__();
            }))
        .__()
        .__();
  }
}
