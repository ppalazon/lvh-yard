package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.AliasTarget;
import dev.palazon.lvhyard.dashboard.services.InstanceSource;
import dev.palazon.lvhyard.dashboard.services.InvalidRegistrationException;
import dev.palazon.lvhyard.dashboard.services.Service;
import dev.palazon.lvhyard.dashboard.services.SourceKind;
import dev.palazon.lvhyard.dashboard.services.Yard;
import java.util.List;
import org.xmlet.htmlapifaster.Element;
import org.xmlet.htmlapifaster.FlowContent;

/** The form that adds an Alias to a Service. The Service page opens it. */
final class AddAliasPage {

  /** The one field of the form. Every refusal shows at it. */
  static final String TARGET = "target";

  private AddAliasPage() {}

  static String path(Service service) {
    return action(service) + "/new";
  }

  static String action(Service service) {
    return ServicePage.path(service) + "/aliases";
  }

  static String removePath(Service service, String id) {
    return action(service) + "/" + id + "/remove";
  }

  /** No link of the navigation bar is current, as on the Service page. */
  private static FormPage.Spec spec(Service service) {
    return new FormPage.Spec(
        null,
        "Add Alias",
        "The Alias gives "
            + service.name()
            + " the Instances of a Remote Service. Only this Workstation's Route uses them.",
        path(service),
        action(service),
        "Add");
  }

  static void openLink(Element<?, ?> parent, Service service) {
    FormPage.openLink(parent, spec(service));
  }

  /** The Remote Services that a new Alias can name: those with Yard Scope. */
  static List<Service> shared(Yard yard) {
    return yard.remotes().stream()
        .flatMap(remote -> remote.services().stream())
        .filter(Service::sharedWithYard)
        .toList();
  }

  /**
   * @param shared the Remote Services the form offers, from {@link #shared}
   * @param target the chosen Remote Service, as {@code <workstation>/<service>}
   * @param error why the last post failed, shown at the field, or {@code null}
   */
  static String render(
      Workstation workstation,
      Service service,
      List<Service> shared,
      String target,
      InvalidRegistrationException error) {
    return FormPage.render(
        workstation,
        spec(service),
        ServicePage.path(service),
        form -> {},
        section -> field(section, service, shared, target, error));
  }

  private static void field(
      FlowContent<?, ?> section,
      Service service,
      List<Service> shared,
      String target,
      InvalidRegistrationException error) {
    List<String> taken =
        service.sources().stream()
            .filter(source -> source.kind() == SourceKind.ALIAS)
            .map(InstanceSource::id)
            .toList();
    InstanceForm.field(
        section,
        TARGET,
        "Remote Service",
        shared.isEmpty()
            ? "No Remote Service has Yard Scope now. An Alias needs one."
            : "Only Remote Services with Yard Scope are listed.",
        error,
        field ->
            field
                .select()
                .attrClass("select")
                .attrId(TARGET)
                .attrName(TARGET)
                .of(select ->
                    shared.forEach(remote -> {
                      AliasTarget remoteTarget =
                          new AliasTarget(remote.workstation(), remote.name());
                      String value = remoteTarget.id();
                      if (!taken.contains(value)) {
                        select
                            .option()
                            .attrValue(Layout.attr(value))
                            .of(option -> option.attrSelected(value.equals(target)))
                            .text(remoteTarget.label())
                            .__();
                      }
                    }))
                .__());
  }
}
