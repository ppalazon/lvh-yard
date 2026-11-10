package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.profiles.RouteProfile;
import java.util.List;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
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
   * @param removeError why the last remove was refused, or {@code null}
   */
  static String render(Workstation workstation, List<RouteProfile> profiles, String removeError) {
    return Layout.page(
        "Route Profiles",
        workstation.name(),
        Layout.Section.ROUTE_PROFILES,
        main ->
            main.div()
                .attrClass("route-profiles-page flex min-w-0 flex-col gap-6")
                .div()
                .attrClass("flex flex-wrap items-center justify-between gap-4")
                .h1()
                .text("Route Profiles")
                .__()
                .a()
                .attrHref(RouteProfileEditorPage.NEW_PATH)
                .attrClass("btn")
                .text("New Route Profile")
                .__()
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
                .of(content -> list(content, profiles, workstation.name()))
                .__());
  }

  private static void list(Div<?> main, List<RouteProfile> profiles, String workstation) {
    if (profiles.isEmpty()) {
      main.p().text("No Route Profiles yet.").__();
      return;
    }
    main.table()
        .attrClass("table route-profiles-table")
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
                    .attrClass("align-top")
                    .a()
                    .attrHref(Layout.attr(RouteProfileViewPage.path(profile.name())))
                    .attrClass(
                        "font-mono text-base font-semibold underline-offset-4 hover:underline")
                    .text(profile.name())
                    .__()
                    .__()
                    .td()
                    .attrClass("align-top")
                    .addAttr("data-label", "Type")
                    .text(profile.type() == null ? "" : profile.type())
                    .__()
                    .td()
                    .attrClass("align-top")
                    .addAttr("data-label", "Used by")
                    .of(cell -> {
                      if (profile.services().isEmpty()) {
                        cell.text("Not used");
                        return;
                      }
                      cell.ul()
                          .attrClass("flex flex-col gap-1")
                          .of(services -> {
                            for (String service : profile.services()) {
                              services
                                  .li()
                                  .a()
                                  .attrHref(Layout.attr(ServicePage.path(workstation, service)))
                                  .attrClass("underline underline-offset-4")
                                  .text(service)
                                  .__()
                                  .__();
                            }
                          })
                          .__();
                    })
                    .__()
                    .of(row -> actions(row, profile.name()))
                    .__()))
        .__()
        .__();
  }

  private static void actions(Tr<?> row, String profile) {
    row.td()
        .attrClass("align-top")
        .div()
        .attrClass("flex flex-wrap gap-2 md:justify-end")
        .a()
        .attrHref(Layout.attr(RouteProfileViewPage.path(profile)))
        .attrClass("btn")
        .addAttr("data-variant", "outline")
        .addAttr("data-size", "sm")
        .addAttr("aria-label", Layout.attr("View " + profile))
        .text("View")
        .__()
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
        .addAttr("data-variant", "destructive")
        .addAttr("data-size", "sm")
        .addAttr("aria-label", Layout.attr("Remove " + profile))
        .text("Remove")
        .__()
        .__()
        .__()
        .__();
  }
}
