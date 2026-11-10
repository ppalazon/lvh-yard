package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.InvalidRegistrationException;
import dev.palazon.lvhyard.dashboard.services.Service;

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
    FormPage.Spec spec =
        new FormPage.Spec(
            null,
            "Edit an Instance of " + service.name(),
            "Instance "
                + instance.id()
                + ", now at "
                + instance.address()
                + ". It stays the same Instance, with the same ID.",
            path(service, instance),
            path(service, instance),
            "Save");
    return FormPage.render(
        workstation,
        spec,
        ServicePage.path(service),
        form -> {},
        section -> InstanceForm.fields(section, fields, defaultHost, error));
  }
}
