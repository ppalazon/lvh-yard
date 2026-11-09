package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.InvalidRegistrationException;

/** The catalog's form that creates an empty Service from its name. */
final class NewServicePage {

  static final String PATH = "/services/new";

  static final String ACTION = "/services";

  static final FormPage.Spec SPEC =
      new FormPage.Spec(
          Layout.Section.CATALOG,
          "New Service",
          "The Service starts empty and Unpublished.",
          PATH,
          ACTION,
          "Create");

  private NewServicePage() {}

  /**
   * @param error why the last post failed, shown at the name field, or {@code null}
   */
  static String render(Workstation workstation, String name, InvalidRegistrationException error) {
    return FormPage.render(
        workstation,
        SPEC,
        form -> {},
        section ->
            InstanceForm.text(
                section,
                "name",
                "Service name",
                name,
                "grafana",
                "Lowercase letters, digits and -.",
                error));
  }
}
