package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.Service;
import java.util.ArrayList;
import java.util.List;
import org.xmlet.htmlapifaster.EnumTypeButtonType;

/** The confirmation step before a Service is deleted, so a click alone deletes nothing. */
final class DeleteServicePage {

  private DeleteServicePage() {}

  static String path(Service service) {
    return ServicePage.path(service) + "/delete";
  }

  static String render(Workstation workstation, Service service) {
    return Layout.page(
        "Delete " + service.name(),
        workstation.name(),
        main ->
            main.div()
                .attrClass("flex max-w-xl flex-col gap-2")
                .h1()
                .text("Delete Service " + service.name())
                .__()
                .p()
                .attrClass("text-muted-foreground")
                .text("Containers keep running. You cannot undo this.")
                .__()
                .__()
                .form()
                .attrClass("card flex max-w-xl flex-col gap-6 p-6")
                .addAttr("method", "post")
                .addAttr("action", Layout.attr(path(service)))
                .div()
                .attrClass("flex flex-col gap-2")
                .p()
                .attrClass("font-medium")
                .text("This removes:")
                .__()
                .ul()
                .attrClass("list-disc pl-5")
                .of(list -> parts(service).forEach(part -> list.li().text(part).__()))
                .__()
                .of(content -> {
                  if (service.instances().stream().anyMatch(Instance::foreign)) {
                    content
                        .p()
                        .attrClass("text-muted-foreground text-sm")
                        .text(
                            "Foreign Instances stay, because another tool registered them. The"
                                + " Service stays while they do.")
                        .__();
                  }
                })
                .__()
                .div()
                .attrClass("flex gap-3")
                .button()
                .attrType(EnumTypeButtonType.SUBMIT)
                .attrClass("btn")
                .addAttr("data-variant", "destructive")
                .text("Delete")
                .__()
                .a()
                .attrHref(Layout.attr(ServicePage.path(service)))
                .attrClass("btn")
                .addAttr("data-variant", "outline")
                .text("Cancel")
                .__()
                .__()
                .__());
  }

  /** What a delete removes, such as {@code its Publication} or {@code 2 Static Instances}. */
  private static List<String> parts(Service service) {
    List<String> parts = new ArrayList<>();
    if (service.published()) {
      parts.add("its Publication");
    }
    long statics = service.instances().stream().filter(Instance::staticInstance).count();
    if (statics > 0) {
      parts.add(count(statics, "Static Instance"));
    }
    int bindings = service.containerBindings().size();
    if (bindings > 0) {
      parts.add(
          count(bindings, "Container Binding")
              + (bindings == 1 ? " with its Bound Instances" : " with their Bound Instances"));
    }
    parts.add("the Service itself");
    return parts;
  }

  private static String count(long count, String thing) {
    return count + " " + thing + (count == 1 ? "" : "s");
  }
}
