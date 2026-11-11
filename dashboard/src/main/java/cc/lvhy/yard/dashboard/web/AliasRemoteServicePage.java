package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.services.InvalidRegistrationException;
import cc.lvhy.yard.dashboard.services.Service;
import java.util.List;
import org.xmlet.htmlapifaster.Element;
import org.xmlet.htmlapifaster.EnumTypeInputType;

/**
 * The form that adds an Alias to a Remote Service, from its row in the catalog. Unlike {@link
 * AddAliasPage}, you choose the Service of this Workstation, and a new name creates it.
 */
final class AliasRemoteServicePage {

  /** The Service of this Workstation that gets the Alias. */
  static final String NAME = "name";

  /** The Remote Service. A refusal of the Workstation or the Service shows here. */
  static final String TARGET = "target";

  private AliasRemoteServicePage() {}

  static String path(Service remote) {
    return action(remote) + "/new";
  }

  static String action(Service remote) {
    return ServicePage.path(remote) + "/alias";
  }

  private static FormPage.Spec spec(Service remote) {
    return new FormPage.Spec(
        Layout.Section.CATALOG,
        "Add Alias",
        "The Alias gives a Service of this Workstation the Instances of "
            + remote.name()
            + " on "
            + remote.workstation()
            + ". A new name creates the Service and publishes it with Local Scope.",
        path(remote),
        action(remote),
        "Add");
  }

  /** Only a Remote Service with Yard Scope can be the target of an Alias. */
  static void button(Element<?, ?> parent, Service remote) {
    parent
        .custom("a")
        .addAttr("href", Layout.attr(path(remote)))
        .addAttr("class", "btn")
        .addAttr("data-variant", "outline")
        .text("Alias")
        .__();
  }

  /**
   * @param own the names of this Workstation's Services, which the name field offers
   * @param name the chosen Service of this Workstation
   * @param error why the last post failed, shown at its field, or {@code null}
   */
  static String render(
      Workstation workstation,
      Service remote,
      List<String> own,
      String name,
      InvalidRegistrationException error) {
    return FormPage.render(
        workstation,
        spec(remote),
        form -> {},
        section -> {
          InstanceForm.field(
              section,
              TARGET,
              "Remote Service",
              null,
              error,
              field ->
                  field
                      .input()
                      .attrType(EnumTypeInputType.TEXT)
                      .attrClass("input")
                      .attrId(TARGET)
                      .attrReadonly(true)
                      .attrValue(Layout.attr(remote.name() + " on " + remote.workstation()))
                      .__());
          InstanceForm.field(
              section,
              NAME,
              "Service of this Workstation",
              "Choose one of your Services, or type a new name.",
              error,
              field ->
                  field
                      .input()
                      .attrType(EnumTypeInputType.TEXT)
                      .attrClass("input")
                      .attrId(NAME)
                      .attrName(NAME)
                      .addAttr("list", NAME + "-options")
                      .attrValue(Layout.attr(name))
                      .of(input -> {
                        if (error != null && error.field().equals(NAME)) {
                          input
                              .addAttr("aria-invalid", "true")
                              .addAttr("aria-describedby", NAME + "-error");
                        }
                      })
                      .__()
                      .datalist()
                      .attrId(NAME + "-options")
                      .of(list ->
                          own.forEach(
                              service -> list.option().attrValue(Layout.attr(service)).__()))
                      .__());
        });
  }
}
