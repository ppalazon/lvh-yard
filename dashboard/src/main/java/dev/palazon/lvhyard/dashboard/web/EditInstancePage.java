package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.InvalidRegistrationException;
import dev.palazon.lvhyard.dashboard.services.Service;
import org.xmlet.htmlapifaster.EnumTypeButtonType;

/** The form that edits a Static Instance, which stays the same Instance. */
final class EditInstancePage {

  private EditInstancePage() {}

  static String path(Service service, Instance instance) {
    return ServicePage.path(service) + "/instances/" + instance.id() + "/edit";
  }

  /**
   * @param defaultHost the host the Instance gets when none is typed
   */
  static String render(
      Workstation workstation,
      Service service,
      Instance instance,
      InstanceForm.Fields fields,
      String defaultHost,
      InvalidRegistrationException error) {
    return Layout.page(
        "Edit " + service.name(),
        workstation.name(),
        main ->
            main.h1()
                .text("Edit an Instance of " + service.name())
                .__()
                .p()
                .text(
                    "Instance "
                        + instance.id()
                        + ", now at "
                        + instance.address()
                        + ". It stays the same Instance, with the same ID.")
                .__()
                .form()
                .attrClass("form flex max-w-md flex-col gap-6")
                .addAttr("method", "post")
                .addAttr("action", Layout.attr(path(service, instance)))
                .of(form -> InstanceForm.fields(form, fields, defaultHost, error))
                .div()
                .attrClass("flex gap-3")
                .button()
                .attrType(EnumTypeButtonType.SUBMIT)
                .attrClass("btn")
                .text("Save")
                .__()
                .a()
                .attrClass("btn")
                .addAttr("data-variant", "outline")
                .attrHref(Layout.attr(ServicePage.path(service)))
                .text("Cancel")
                .__()
                .__()
                .__());
  }
}
