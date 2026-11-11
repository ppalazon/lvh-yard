package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.services.InvalidRegistrationException;
import cc.lvhy.yard.dashboard.services.Service;
import org.xmlet.htmlapifaster.Element;

/** The form that adds a Static Instance to a Service. The Service page opens it. */
final class AddStaticInstancePage {

  private AddStaticInstancePage() {}

  static String path(Service service) {
    return action(service) + "/new";
  }

  static String action(Service service) {
    return ServicePage.path(service) + "/instances";
  }

  /** No link of the navigation bar is current, as on the Service page. */
  private static FormPage.Spec spec(Service service) {
    return new FormPage.Spec(
        null,
        "Add Static Instance",
        "The Instance joins " + service.name() + ", at a fixed host and port.",
        path(service),
        action(service),
        "Add");
  }

  static void openLink(Element<?, ?> parent, Service service) {
    FormPage.openCardLink(parent, spec(service));
  }

  /**
   * @param defaultHost the host a Static Instance gets when none is typed
   * @param error why the last post failed, shown at its field, or {@code null}
   */
  static String render(
      Workstation workstation,
      Service service,
      InstanceForm.Fields fields,
      String defaultHost,
      InvalidRegistrationException error) {
    return FormPage.render(
        workstation,
        spec(service),
        ServicePage.path(service),
        form -> {},
        section -> InstanceForm.fields(section, fields, defaultHost, error));
  }
}
