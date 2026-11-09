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
            main.h1()
                .text("Delete Service " + service.name())
                .__()
                .p()
                .text("This removes " + String.join(", ", parts(service)) + ".")
                .__()
                .p()
                .text("Containers keep running. You cannot undo this.")
                .__()
                .of(content -> {
                  if (service.instances().stream().anyMatch(Instance::foreign)) {
                    content
                        .p()
                        .text(
                            "Foreign Instances stay, because another tool registered them. The"
                                + " Service stays while they do.")
                        .__();
                  }
                })
                .form()
                .attrClass("flex gap-2")
                .addAttr("method", "post")
                .addAttr("action", Layout.attr(path(service)))
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
                .__());
  }

  /** What a delete removes, such as {@code its Publication} or {@code 2 Static Instances}. */
  private static List<String> parts(Service service) {
    List<String> parts = new ArrayList<>();
    if (service.published()) {
      parts.add("its Publication");
    }
    long statics = service.instances().stream().filter(Instance::staticInstance).count();
    parts.add(count(statics, "Static Instance"));
    int bindings = service.containerBindings().size();
    parts.add(
        count(bindings, "Container Binding")
            + switch (bindings) {
              case 0 -> "";
              case 1 -> " with its Bound Instances";
              default -> " with their Bound Instances";
            });
    parts.add("the Service itself");
    return parts;
  }

  private static String count(long count, String thing) {
    return count + " " + thing + (count == 1 ? "" : "s");
  }
}
