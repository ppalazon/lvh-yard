package dev.palazon.lvhyard.dashboard.web;

import htmlflow.HtmlFlow;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;
import org.springframework.http.MediaType;
import org.springframework.web.util.HtmlUtils;
import org.xmlet.htmlapifaster.Button;
import org.xmlet.htmlapifaster.Div;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.Main;

/**
 * The shell every page shares: the stylesheet built from Tailwind CSS and Basecoat ({@code mise run
 * css}), and HTMX from its WebJar. Tailwind only builds the classes it finds written out whole in
 * the views, so a view never builds a class name from parts. Pages that change by themselves, as
 * containers start and stop, put that part in a {@link #refreshed} region. HtmlFlow escapes text
 * but not attribute values, so views pass any value from data through {@link #attr}.
 */
final class Layout {

  static final MediaType HTML = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

  private Layout() {}

  /** A link of the navigation bar in the header. */
  enum Section {
    CATALOG("Catalog", "/"),
    CONTAINERS("Containers", ContainersPage.PATH),
    ALIASES("Aliases", AliasesPage.PATH),
    STATIC_INSTANCES("Static Instances", StaticInstancesPage.PATH),
    ROUTE_PROFILES("Route Profiles", RouteProfilesPage.PATH),
    BACKUPS("Backups", BackupsPage.PATH);

    final String label;
    final String path;

    Section(String label, String path) {
      this.label = label;
      this.path = path;
    }
  }

  /** A page that is none of the {@link Section}s, so no link of the navigation bar is current. */
  static String page(String title, String workstation, Consumer<Main<?>> content) {
    return page(title, workstation, null, content);
  }

  static String page(String title, String workstation, Section current, Consumer<Main<?>> content) {
    StringBuilder out = new StringBuilder();
    HtmlFlow.doc(out)
        .setIndented(false)
        .html()
        .attrLang("en")
        .head()
        .meta()
        .attrCharset("utf-8")
        .__()
        .meta()
        .addAttr("name", "viewport")
        .attrContent("width=device-width, initial-scale=1")
        .__()
        .title()
        .text(title + " · " + workstation + " · LVH Yard")
        .__()
        .link()
        .addAttr("rel", "stylesheet")
        .attrHref("/app.css")
        .__()
        .script()
        .attrSrc("/theme.js")
        .__()
        .script()
        .attrSrc("/webjars/htmx.org/dist/htmx.min.js")
        .__()
        .__()
        .body()
        .header()
        .attrClass("border-b")
        .div()
        .attrClass(
            "mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-x-4 gap-y-2 px-4"
                + " py-3")
        .a()
        .attrHref("/")
        .attrClass("font-semibold")
        .text("LVH Yard · " + workstation)
        .__()
        .div()
        .attrClass("flex items-center gap-3 sm:order-last")
        .of(Layout::statusDots)
        .of(Layout::themeToggle)
        .__()
        .of(bar -> navigation(bar, current))
        .__()
        .__()
        .main()
        .attrClass("mx-auto flex max-w-6xl flex-col gap-8 px-4 py-8")
        .of(main -> content.accept(main))
        .__()
        .__()
        .__();
    return out.toString();
  }

  /** On a phone, the links take a row of their own below the title. */
  private static void navigation(Div<?> bar, Section current) {
    bar.nav()
        .addAttr("aria-label", "Main")
        .attrClass("w-full sm:w-auto sm:flex-1")
        .ul()
        .attrClass("flex flex-wrap gap-1")
        .of(list -> {
          for (Section section : Section.values()) {
            list.li()
                .a()
                .attrHref(section.path)
                .attrClass("btn")
                .addAttr("data-size", "sm")
                .addAttr("data-variant", section == current ? "secondary" : "ghost")
                .of(link -> {
                  if (section == current) {
                    link.addAttr("aria-current", "page");
                  }
                })
                .text(section.label)
                .__()
                .__();
          }
        })
        .__()
        .__();
  }

  /**
   * Where the {@link StatusSummary} dots go. HTMX loads them once the page is shown, and again
   * every 5 seconds, so a slow part never slows the page. An error answer swaps nothing, so the
   * dots keep their last state, as in a {@link #refreshed} region.
   */
  private static void statusDots(Div<?> header) {
    header
        .div()
        .attrId("status-dots")
        .addAttr("hx-get", StatusSummary.PATH)
        .addAttr("hx-trigger", "load, every 5s")
        .addAttr("hx-swap", "innerHTML")
        .addAttr("hx-status:4xx", "swap:none")
        .addAttr("hx-status:5xx", "swap:none")
        .__();
  }

  /**
   * {@code theme.js} handles it. It shows a moon in the light theme and a sun in the dark one
   * (Lucide's icons).
   */
  private static void themeToggle(Div<?> header) {
    header
        .button()
        .attrType(EnumTypeButtonType.BUTTON)
        .attrId("theme-toggle")
        .attrClass("btn")
        .addAttr("data-variant", "ghost")
        .addAttr("data-size", "icon")
        .addAttr("aria-label", "Dark theme")
        .addAttr("aria-pressed", "false")
        .of(button -> icon(button, "dark:hidden", "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"))
        .of(button ->
            icon(
                button,
                "hidden dark:block",
                "M12 2v2",
                "M12 20v2",
                "m4.93 4.93 1.41 1.41",
                "m17.66 17.66 1.41 1.41",
                "M2 12h2",
                "M20 12h2",
                "m6.34 17.66-1.41 1.41",
                "m19.07 4.93-1.41 1.41",
                "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0"))
        .__();
  }

  private static void icon(Button<?> button, String classes, String... paths) {
    button
        .svg()
        .attrClass(classes)
        .addAttr("viewBox", "0 0 24 24")
        .addAttr("fill", "none")
        .addAttr("stroke", "currentColor")
        .addAttr("stroke-width", "2")
        .addAttr("stroke-linecap", "round")
        .addAttr("stroke-linejoin", "round")
        .addAttr("aria-hidden", "true")
        .of(svg -> Arrays.stream(paths).forEach(path -> svg.custom("path").addAttr("d", path).__()))
        .__();
  }

  /**
   * A region of the page, with id {@code id}, that HTMX loads again from {@code path} every 5
   * seconds and swaps for the same region of the answer. What is outside it, such as a form being
   * filled in, stays as it is. An error answer, such as while a Workstation is unreachable, swaps
   * nothing: HTMX 4 would otherwise swap the region for the error page, which has no such region,
   * and the refresh would stop for good.
   */
  static void refreshed(Main<?> main, String id, String path, Consumer<Div<?>> content) {
    main.div()
        .attrId(id)
        .attrClass("flex flex-col gap-8")
        .addAttr("hx-get", attr(path))
        .addAttr("hx-trigger", "every 5s")
        .addAttr("hx-select", "#" + id)
        .addAttr("hx-swap", "outerHTML")
        .addAttr("hx-status:4xx", "swap:none")
        .addAttr("hx-status:5xx", "swap:none")
        .of(div -> content.accept(div))
        .__();
  }

  static String attr(String value) {
    return HtmlUtils.htmlEscape(value);
  }
}
