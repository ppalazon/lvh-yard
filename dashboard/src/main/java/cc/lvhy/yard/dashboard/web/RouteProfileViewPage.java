package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.profiles.RouteProfileDetail;

final class RouteProfileViewPage {

  private RouteProfileViewPage() {}

  static String path(String profile) {
    return RouteProfilesPage.PATH + "/" + profile + "/view";
  }

  static String render(Workstation workstation, RouteProfileDetail profile) {
    return Layout.page(
        profile.name(),
        workstation.name(),
        Layout.Section.ROUTE_PROFILES,
        main ->
            main.div()
                .attrClass("route-profiles-page flex min-w-0 flex-col gap-6")
                .a()
                .attrHref(RouteProfilesPage.PATH)
                .attrClass("text-muted-foreground w-fit text-sm underline underline-offset-4")
                .text("Back to Route Profiles")
                .__()
                .div()
                .attrClass("flex flex-wrap items-center justify-between gap-4")
                .h1()
                .attrClass("min-w-0 font-mono")
                .text(profile.name())
                .__()
                .a()
                .attrHref(Layout.attr(RouteProfilesPage.editPath(profile.name())))
                .attrClass("btn")
                .addAttr("data-variant", "outline")
                .text("Change")
                .__()
                .__()
                .section()
                .attrClass("flex min-w-0 flex-col gap-3")
                .h2()
                .text("Route options")
                .__()
                .pre()
                .attrClass(
                    "bg-muted max-w-full overflow-x-auto rounded-md border p-4 text-sm"
                        + " leading-relaxed focus-visible:outline-2 focus-visible:outline-ring"
                        + " focus-visible:outline-offset-2")
                .addAttr("tabindex", "0")
                .addAttr("aria-label", "Route options YAML")
                .code()
                .text(RouteProfileYaml.write(profile.definition()))
                .__()
                .__()
                .__()
                .__());
  }
}
