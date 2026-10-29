package dev.palazon.lvhyard.dashboard.web;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;

import org.springframework.http.MediaType;
import org.springframework.web.util.HtmlUtils;
import org.xmlet.htmlapifaster.Button;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.Main;
import org.xmlet.htmlapifaster.Nav;

import htmlflow.HtmlFlow;

/**
 * The shell every page shares: the stylesheet built from Tailwind CSS and Basecoat ({@code mise run css}), and HTMX
 * from its WebJar. Tailwind only builds the classes it finds written out whole in the views, so a view never builds
 * a class name from parts.
 * HtmlFlow escapes text but not attribute values, so views pass any value from data through {@link #attr}.
 */
final class Layout {

    /** What pages and fragments are sent as. */
    static final MediaType HTML = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

    private Layout() {
    }

    static String page(String title, String workstation, Consumer<Main<?>> content) {
        StringBuilder out = new StringBuilder();
        HtmlFlow.doc(out).setIndented(false)
                .html().attrLang("en")
                    .head()
                        .meta().attrCharset("utf-8").__()
                        .meta().addAttr("name", "viewport").attrContent("width=device-width, initial-scale=1").__()
                        .title().text(title + " · " + workstation + " · LVH Yard").__()
                        .link().addAttr("rel", "stylesheet").attrHref("/app.css").__()
                        .script().attrSrc("/theme.js").__()
                        .script().attrSrc("/webjars/htmx.org/dist/htmx.min.js").__()
                    .__()
                    .body()
                        .header().attrClass("border-b")
                            .nav().attrClass("mx-auto flex max-w-6xl items-center justify-between px-4 py-3")
                                .a().attrHref("/").attrClass("font-semibold").text("LVH Yard · " + workstation).__()
                                .of(Layout::themeToggle)
                            .__()
                        .__()
                        .main().attrClass("mx-auto flex max-w-6xl flex-col gap-8 px-4 py-8")
                                .of(main -> content.accept(main)).__()
                    .__()
                .__();
        return out.toString();
    }

    /**
     * The button that switches between the light and dark theme, which {@code theme.js} handles. It shows a moon in
     * the light theme and a sun in the dark one (Lucide's icons).
     */
    private static void themeToggle(Nav<?> nav) {
        nav.button().attrType(EnumTypeButtonType.BUTTON).attrId("theme-toggle").attrClass("btn")
                .addAttr("data-variant", "ghost").addAttr("data-size", "icon")
                .addAttr("aria-label", "Dark theme").addAttr("aria-pressed", "false")
                .of(button -> icon(button, "dark:hidden", "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"))
                .of(button -> icon(button, "hidden dark:block", "M12 2v2", "M12 20v2", "m4.93 4.93 1.41 1.41",
                        "m17.66 17.66 1.41 1.41", "M2 12h2", "M20 12h2", "m6.34 17.66-1.41 1.41",
                        "m19.07 4.93-1.41 1.41", "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0"))
        .__();
    }

    /** A 24×24 line icon drawn with {@code paths}, shown as {@code classes} say. */
    private static void icon(Button<?> button, String classes, String... paths) {
        button.svg().attrClass(classes).addAttr("viewBox", "0 0 24 24").addAttr("fill", "none")
                .addAttr("stroke", "currentColor").addAttr("stroke-width", "2").addAttr("stroke-linecap", "round")
                .addAttr("stroke-linejoin", "round").addAttr("aria-hidden", "true")
                .of(svg -> Arrays.stream(paths).forEach(path -> svg.custom("path").addAttr("d", path).__()))
        .__();
    }

    /** {@code value} escaped for an attribute. */
    static String attr(String value) {
        return HtmlUtils.htmlEscape(value);
    }
}
