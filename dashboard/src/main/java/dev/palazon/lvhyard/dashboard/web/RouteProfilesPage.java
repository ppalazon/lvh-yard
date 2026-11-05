package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.profiles.RouteProfile;
import java.util.List;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.EnumTypeInputType;
import org.xmlet.htmlapifaster.Main;
import org.xmlet.htmlapifaster.Tr;

final class RouteProfilesPage {

  static final String PATH = "/route-profiles";

  private RouteProfilesPage() {}

  /** The row of {@code profile} in the list. */
  static String path(String profile) {
    return PATH + "#" + rowId(profile);
  }

  static String editPath(String profile) {
    return PATH + "/" + profile + "/edit";
  }

  static String removePath(String profile) {
    return PATH + "/" + profile + "/remove";
  }

  private static String rowId(String profile) {
    return "profile-" + profile;
  }

  /**
   * What the form shows, as typed.
   *
   * @param yaml the profile in Traefik's format, as YAML
   * @param errors why the last save was refused, each a full sentence
   */
  record Form(String name, String yaml, List<String> errors) {

    static final Form EMPTY = new Form("", "", List.of());

    Form {
      name = name == null ? "" : name;
      yaml = yaml == null ? "" : yaml;
      errors = List.copyOf(errors);
    }
  }

  /**
   * @param removeError why the last remove was refused, or {@code null}
   */
  static String render(
      Workstation workstation, List<RouteProfile> profiles, Form form, String removeError) {
    return Layout.page(
        "Route Profiles",
        workstation.name(),
        Layout.Section.ROUTE_PROFILES,
        main ->
            main.h1()
                .text("Route Profiles")
                .__()
                .p()
                .text(
                    "Reusable Route options, such as authentication or added headers, that the"
                        + " Publications of "
                        + workstation.name()
                        + " can name.")
                .__()
                .of(content -> {
                  if (removeError != null) {
                    content
                        .div()
                        .attrClass("alert")
                        .addAttr("data-variant", "destructive")
                        .addAttr("role", "alert")
                        .h3()
                        .text("Not removed")
                        .__()
                        .section()
                        .p()
                        .text(removeError)
                        .__()
                        .__()
                        .__();
                  }
                })
                .of(content -> list(content, profiles))
                .h2()
                .text("Add or change a Route Profile")
                .__()
                .of(RouteProfilesPage::examples)
                .of(content -> form(content, form)));
  }

  /** It loads the page again with the example in the form, so it needs no script. */
  private static void examples(Main<?> main) {
    main.form()
        .attrClass("flex flex-wrap items-end gap-2")
        .addAttr("method", "get")
        .addAttr("action", PATH)
        .div()
        .attrClass("field")
        .label()
        .attrFor("example")
        .text("Example")
        .__()
        .select()
        .attrClass("select")
        .attrId("example")
        .attrName("example")
        .of(select -> {
          for (RouteProfileExample example : RouteProfileExample.values()) {
            select.option().attrValue(example.key).text(example.label).__();
          }
        })
        .__()
        .__()
        .button()
        .attrType(EnumTypeButtonType.SUBMIT)
        .attrClass("btn")
        .addAttr("data-variant", "outline")
        .text("New profile")
        .__()
        .__();
  }

  private static void list(Main<?> main, List<RouteProfile> profiles) {
    if (profiles.isEmpty()) {
      main.p().text("No Route Profiles yet.").__();
      return;
    }
    main.div()
        .attrClass("table-container")
        .table()
        .attrClass("table")
        .thead()
        .tr()
        .th()
        .text("Name")
        .__()
        .th()
        .text("Type")
        .__()
        .th()
        .text("Used by")
        .__()
        .th()
        .of(cell -> cell.span().attrClass("sr-only").text("Actions").__())
        .__()
        .__()
        .__()
        .tbody()
        .of(body ->
            profiles.forEach(profile ->
                body.tr()
                    .attrId(Layout.attr(rowId(profile.name())))
                    .td()
                    .attrClass("align-top font-mono")
                    .text(profile.name())
                    .__()
                    .td()
                    .attrClass("align-top")
                    .text(profile.type() == null ? "" : profile.type())
                    .__()
                    .td()
                    .attrClass("align-top")
                    .text(
                        profile.services().isEmpty()
                            ? "Not used"
                            : String.join(", ", profile.services()))
                    .__()
                    .of(row -> actions(row, profile.name()))
                    .__()))
        .__()
        .__()
        .__();
  }

  private static void actions(Tr<?> row, String profile) {
    row.td()
        .attrClass("align-top")
        .div()
        .attrClass("flex justify-end gap-2")
        .a()
        .attrHref(Layout.attr(editPath(profile)))
        .attrClass("btn")
        .addAttr("data-variant", "outline")
        .addAttr("data-size", "sm")
        .addAttr("aria-label", Layout.attr("Change " + profile))
        .text("Change")
        .__()
        .form()
        .addAttr("method", "post")
        .addAttr("action", Layout.attr(removePath(profile)))
        .button()
        .attrType(EnumTypeButtonType.SUBMIT)
        .attrClass("btn")
        .addAttr("data-variant", "outline")
        .addAttr("data-size", "sm")
        .addAttr("aria-label", Layout.attr("Remove " + profile))
        .text("Remove")
        .__()
        .__()
        .__()
        .__();
  }

  /** A name already in the list replaces that profile. */
  private static void form(Main<?> main, Form form) {
    main.form()
        .attrClass("form flex max-w-2xl flex-col gap-6")
        .addAttr("method", "post")
        .addAttr("action", PATH)
        .of(content -> {
          if (form.errors().isEmpty()) {
            return;
          }
          content
              .div()
              .attrClass("alert")
              .addAttr("data-variant", "destructive")
              .addAttr("role", "alert")
              .h3()
              .text("Not saved")
              .__()
              .section()
              .ul()
              .of(list ->
                  form.errors()
                      .forEach(
                          error -> list.li().attrClass("whitespace-pre-wrap").text(error).__()))
              .__()
              .__()
              .__();
        })
        .div()
        .attrClass("field")
        .label()
        .attrFor("name")
        .text("Name")
        .__()
        .input()
        .attrType(EnumTypeInputType.TEXT)
        .attrClass("input")
        .attrId("name")
        .attrName("name")
        .attrValue(Layout.attr(form.name()))
        .attrPlaceholder("team-login")
        .__()
        .p()
        .attrClass("text-muted-foreground text-sm")
        .text("Letters, digits, - and _. A name in the list replaces that profile.")
        .__()
        .__()
        .div()
        .attrClass("field")
        .label()
        .attrFor("definition")
        .text("Route options")
        .__()
        .textarea()
        .attrClass("textarea font-mono")
        .attrId("definition")
        .attrName("definition")
        .attrRows(12)
        .addAttr("spellcheck", "false")
        .text(form.yaml())
        .__()
        .p()
        .attrClass("text-muted-foreground text-sm")
        .text(
            "One type of Route option in Traefik's YAML, such as headers: with its options below"
                + " it.")
        .__()
        .__()
        .button()
        .attrType(EnumTypeButtonType.SUBMIT)
        .attrClass("btn w-fit")
        .text("Save")
        .__()
        .__();
  }
}
