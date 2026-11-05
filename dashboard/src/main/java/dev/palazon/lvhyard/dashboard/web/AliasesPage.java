package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.Alias;
import dev.palazon.lvhyard.dashboard.services.InvalidRegistrationException;
import dev.palazon.lvhyard.dashboard.services.Service;
import dev.palazon.lvhyard.dashboard.services.Yard;
import java.util.List;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.Main;
import org.xmlet.htmlapifaster.Td;
import org.xmlet.htmlapifaster.Tr;

final class AliasesPage {

  static final String PATH = "/aliases";

  private static final String ADD_DIALOG = "add-alias";

  private AliasesPage() {}

  static String removePath(Alias alias) {
    return PATH + "/" + alias.name() + "/remove";
  }

  /**
   * @param target the chosen Remote Service, as {@code <workstation>/<service>}
   */
  static String render(
      Workstation workstation,
      Yard yard,
      List<Alias> aliases,
      String name,
      String target,
      InvalidRegistrationException error) {
    List<Service> shared =
        yard.remotes().stream()
            .flatMap(remote -> remote.services().stream())
            .filter(Service::sharedWithYard)
            .toList();
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
                  if (shared.isEmpty()) {
                    content
                        .p()
                        .text(
                            "No Remote Service is shared with the Yard yet: an Alias needs one"
                                + " with Yard Scope.")
                        .__();
                  } else {
                    Dialogs.openButton(content, ADD_DIALOG, "Add an Alias");
                  }
                })
                .of(content -> list(content, aliases))
                .of(content -> {
                  if (!shared.isEmpty()) {
                    Dialogs.modal(
                        content,
                        ADD_DIALOG,
                        "Add an Alias",
                        error != null,
                        body -> form(body, shared, name, target, error));
                  }
                }));
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
          case HIDDEN -> "Hidden";
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

  private static void form(
      Div<?> body,
      List<Service> shared,
      String name,
      String target,
      InvalidRegistrationException error) {
    body.form()
        .attrClass("form flex flex-col gap-6")
        .addAttr("method", "post")
        .addAttr("action", PATH)
        .of(form ->
            InstanceForm.text(
                form, "name", "Alias", name, "grafana", "Lowercase letters, digits and -.", error))
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
        .__()
        .of(form -> Dialogs.actions(form, "Add"))
        .__();
  }
}
