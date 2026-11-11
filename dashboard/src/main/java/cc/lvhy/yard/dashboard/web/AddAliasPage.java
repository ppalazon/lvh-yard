package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.services.AliasTarget;
import cc.lvhy.yard.dashboard.services.InstanceSource;
import cc.lvhy.yard.dashboard.services.InvalidRegistrationException;
import cc.lvhy.yard.dashboard.services.Service;
import cc.lvhy.yard.dashboard.services.SourceKind;
import cc.lvhy.yard.dashboard.services.Yard;
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
    FormPage.openCardLink(parent, spec(service));
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
    List<String> taken =
        service.sources().stream()
            .filter(source -> source.kind() == SourceKind.ALIAS)
            .map(InstanceSource::id)
            .toList();
    List<AliasTarget> offered =
        shared.stream()
            .map(remote -> new AliasTarget(remote.workstation(), remote.name()))
            .filter(remote -> !taken.contains(remote.id()))
            .toList();
    return FormPage.render(
        workstation,
        spec(service),
        ServicePage.path(service),
        !offered.isEmpty(),
        form -> {},
        section -> {
          if (offered.isEmpty()) {
            section
                .p()
                .attrClass("text-muted-foreground")
                .text(
                    shared.isEmpty()
                        ? "No Remote Service has Yard Scope now. An Alias needs one."
                        : "Every Remote Service with Yard Scope is an Alias of this Service"
                            + " already.")
                .__();
            if (error != null) {
              section
                  .p()
                  .attrId(TARGET + "-error")
                  .addAttr("role", "alert")
                  .attrClass("text-destructive text-sm")
                  .text(error.getMessage())
                  .__();
            }
          } else {
            field(section, offered, target, error);
          }
        });
  }

  private static void field(
      FlowContent<?, ?> section,
      List<AliasTarget> offered,
      String target,
      InvalidRegistrationException error) {
    InstanceForm.field(
        section,
        TARGET,
        "Remote Service",
        "Only Remote Services with Yard Scope are listed.",
        error,
        field ->
            field
                .select()
                .attrClass("select w-full")
                .attrId(TARGET)
                .attrName(TARGET)
                .of(select ->
                    offered.forEach(remote ->
                        select
                            .option()
                            .attrValue(Layout.attr(remote.id()))
                            .of(option -> option.attrSelected(remote.id().equals(target)))
                            .text(remote.label())
                            .__()))
                .__());
  }
}
