package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.Workstation;
import java.util.List;
import org.xmlet.htmlapifaster.EnumTypeButtonType;
import org.xmlet.htmlapifaster.EnumTypeInputType;
import org.xmlet.htmlapifaster.FlowContent;
import org.xmlet.htmlapifaster.P;

final class RouteProfileEditorPage {

  static final String NEW_PATH = RouteProfilesPage.PATH + "/new";

  private RouteProfileEditorPage() {}

  /** The typed values stay in the form when a save is refused. */
  record Form(String name, String yaml, List<String> errors) {

    static final Form EMPTY = new Form("", "", List.of());

    Form {
      name = name == null ? "" : name;
      yaml = yaml == null ? "" : yaml;
      errors = List.copyOf(errors);
    }
  }

  static String render(Workstation workstation, Form form, boolean editing) {
    String title = editing ? "Change Route Profile" : "New Route Profile";
    return Layout.page(
        title,
        workstation.name(),
        Layout.Section.ROUTE_PROFILES,
        main ->
            main.div()
                .attrClass("route-profile-editor flex flex-col gap-6")
                .header()
                .attrClass("flex flex-wrap items-center justify-between gap-4")
                .h1()
                .text(title)
                .__()
                .a()
                .attrHref(RouteProfilesPage.PATH)
                .attrClass("btn")
                .addAttr("data-variant", "outline")
                .text("Back to Route Profiles")
                .__()
                .__()
                .div()
                .attrClass("route-profile-workspace")
                .of(content -> form(content, form, editing))
                .of(RouteProfileEditorPage::examples)
                .__()
                .__());
  }

  private static void form(FlowContent<?, ?> content, Form form, boolean editing) {
    content
        .form()
        .attrClass("form flex min-w-0 flex-col gap-6")
        .addAttr("method", "post")
        .addAttr("action", RouteProfilesPage.PATH)
        .of(fields -> {
          if (form.errors().isEmpty()) {
            return;
          }
          fields
              .div()
              .attrClass("alert")
              .addAttr("data-variant", "destructive")
              .addAttr("role", "alert")
              .h2()
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
        .attrClass("input font-mono")
        .attrId("name")
        .attrName("name")
        .attrValue(Layout.attr(form.name()))
        .attrPlaceholder("team-login")
        .addAttr("aria-describedby", "profile-name-help")
        .__()
        .p()
        .attrId("profile-name-help")
        .attrClass("text-muted-foreground text-sm")
        .text("Letters, digits, - and _. Start with a letter or digit.")
        .__()
        .__()
        .p()
        .attrClass("border-risk/40 text-risk border-l-2 pl-3 text-sm")
        .text(
            editing
                ? "Saving this name replaces the profile for every Service and chain that uses it."
                : "If the name exists, saving replaces its profile for every Service and chain that"
                    + " uses it.")
        .__()
        .div()
        .attrClass("field")
        .label()
        .attrFor("definition")
        .text("Route options")
        .__()
        .p()
        .attrId("profile-definition-help")
        .attrClass("text-muted-foreground text-sm")
        .text("Write one middleware, or clientCertificate, as YAML.")
        .__()
        .textarea()
        .attrClass("textarea profile-yaml font-mono")
        .attrId("definition")
        .attrName("definition")
        .attrRows(26)
        .addAttr("spellcheck", "false")
        .addAttr("autocapitalize", "off")
        .addAttr("aria-describedby", "profile-definition-help")
        .text(form.yaml())
        .__()
        .__()
        .div()
        .attrClass("flex flex-wrap items-center gap-3")
        .button()
        .attrType(EnumTypeButtonType.SUBMIT)
        .attrClass("btn")
        .text(editing ? "Save changes" : "Save Route Profile")
        .__()
        .a()
        .attrHref(RouteProfilesPage.PATH)
        .attrClass("btn")
        .addAttr("data-variant", "outline")
        .text("Cancel")
        .__()
        .__()
        .__();
  }

  private static void examples(FlowContent<?, ?> content) {
    content
        .aside()
        .attrClass("profile-reference")
        .addAttr("aria-labelledby", "profile-examples-heading")
        .div()
        .h2()
        .attrId("profile-examples-heading")
        .text("Examples")
        .__()
        .p()
        .attrClass("text-muted-foreground mb-4 mt-2 text-sm")
        .text("Copy an example into Route options, then change its values.")
        .__()
        .of(aside -> {
          for (RouteProfileExample example : RouteProfileExample.values()) {
            aside
                .custom("details")
                .attrClass("profile-example")
                .custom("summary")
                .text(example.label)
                .__()
                .div()
                .attrClass("flex min-w-0 flex-col gap-3 pb-4")
                .p()
                .attrClass("text-muted-foreground text-sm")
                .of(p -> withCode(p, example.description))
                .__()
                .pre()
                .code()
                .text(example.text)
                .__()
                .__()
                .a()
                .attrHref(Layout.attr(example.reference))
                .attrClass("w-fit text-sm underline underline-offset-4")
                .text("Traefik documentation")
                .__()
                .__()
                .__();
          }
        })
        .__()
        .__();
  }

  /** Writes the text, with each part between backticks as inline code. */
  private static void withCode(P<?> paragraph, String text) {
    String[] parts = text.split("`", -1);
    for (int i = 0; i < parts.length; i++) {
      if (i % 2 != 0) {
        paragraph.code().attrClass("rounded bg-muted px-1.5 py-0.5 font-mono").text(parts[i]).__();
      } else if (!parts[i].isEmpty()) {
        paragraph.text(parts[i]);
      }
    }
  }
}
