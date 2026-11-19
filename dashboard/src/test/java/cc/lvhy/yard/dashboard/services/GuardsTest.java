package cc.lvhy.yard.dashboard.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import cc.lvhy.yard.dashboard.RuntimeNetwork;
import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.consul.FakeConsul;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The checks at the start of the public methods of Services, Publications, Aliases and
 * ContainerBindings: another Workstation's Service and a name that is no DNS label are refused
 * before anything is written. The controllers check the same things first, so no request over HTTP
 * reaches these checks. The classes are public, and they stay safe on their own.
 */
class GuardsTest {

  private static final String BAD_NAME = "Not_A_Label";

  private final Workstation workstation = new Workstation("home", "lvh.me");

  private final FakeConsul consul = new FakeConsul("home");

  private final ServiceRecords records = new ServiceRecords(consul, consul);

  private final Registrations registrations =
      new Registrations(
          workstation, consul, records, Duration.ofSeconds(10), "", new RuntimeNetwork(false));

  private final Publications publications =
      new Publications(workstation, consul, records, null, event -> {});

  private final ContainerBindings bindings =
      new ContainerBindings(workstation, consul, registrations, records, event -> {});

  private final Services services =
      new Services(workstation, records, publications, registrations, bindings);

  private final Aliases aliases =
      new Aliases(workstation, records, mock(YardReader.class), publications);

  private void assertNothingWritten() {
    assertThat(consul.values(null, "lvh-yard/")).isEmpty();
  }

  @Test
  void aServiceOfAnotherWorkstationIsRefusedAndNothingIsWritten() {
    assertThatThrownBy(() -> services.create("away", "shop"))
        .isInstanceOf(RemoteServiceException.class);
    assertThatThrownBy(() -> services.restore("away", "shop"))
        .isInstanceOf(RemoteServiceException.class);
    assertThatThrownBy(() -> services.delete("away", "shop"))
        .isInstanceOf(RemoteServiceException.class);
    assertThatThrownBy(() ->
            services.setInstancePriority(
                "away", "shop", new InstancePriorityRequest(List.of(), true)))
        .isInstanceOf(RemoteServiceException.class);
    assertThatThrownBy(() -> publications.publish("away", "shop", publicationRequest()))
        .isInstanceOf(RemoteServiceException.class);
    assertThatThrownBy(() -> publications.unpublish("away", "shop"))
        .isInstanceOf(RemoteServiceException.class);
    assertNothingWritten();
  }

  @Test
  void aNameThatIsNoDnsLabelIsRefusedAndNothingIsWritten() {
    assertThatThrownBy(() -> services.create("home", BAD_NAME))
        .isInstanceOf(InvalidRegistrationException.class);
    assertThatThrownBy(() -> services.restore("home", BAD_NAME))
        .isInstanceOf(InvalidRegistrationException.class);
    assertThatThrownBy(() -> services.delete("home", BAD_NAME))
        .isInstanceOf(InvalidRegistrationException.class);
    assertThatThrownBy(() ->
            services.setInstancePriority(
                "home", BAD_NAME, new InstancePriorityRequest(List.of(), true)))
        .isInstanceOf(InvalidRegistrationException.class);
    assertThatThrownBy(() -> publications.publish("home", BAD_NAME, publicationRequest()))
        .isInstanceOf(InvalidRegistrationException.class);
    assertNothingWritten();
  }

  @Test
  void anAliasWithABadNameOrTargetIsRefusedAndNothingIsWritten() {
    assertThatThrownBy(() -> aliases.add(BAD_NAME, "away", "grafana", true))
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessageContaining(BAD_NAME);
    assertThatThrownBy(() -> aliases.add("shop", BAD_NAME, "grafana", true))
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessageContaining("is not a single DNS label")
        .extracting("field")
        .isEqualTo("workstation");
    assertThatThrownBy(() -> aliases.add("shop", "away", BAD_NAME, true))
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessageContaining("is not a single DNS label")
        .extracting("field")
        .isEqualTo("service");
    assertThatThrownBy(() -> aliases.add("shop", "home", "grafana", true))
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessageContaining("this Workstation");
    assertThatThrownBy(() -> aliases.restore(BAD_NAME, "away", "grafana"))
        .isInstanceOf(InvalidRegistrationException.class);
    assertThatThrownBy(() -> aliases.restore("shop", BAD_NAME, "grafana"))
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessageContaining("is not a single DNS label")
        .extracting("field")
        .isEqualTo("workstation");
    assertThatThrownBy(() -> aliases.restore("shop", "away", BAD_NAME))
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessageContaining("is not a single DNS label")
        .extracting("field")
        .isEqualTo("service");
    assertThatThrownBy(() -> aliases.restore("shop", "home", "grafana"))
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessageContaining("this Workstation");
    assertThatThrownBy(() -> aliases.remove(BAD_NAME, "away", "grafana"))
        .isInstanceOf(InvalidRegistrationException.class);
    assertThatThrownBy(() -> aliases.of(BAD_NAME)).isInstanceOf(InvalidRegistrationException.class);
    assertNothingWritten();
  }

  @Test
  void aContainerBindingOfAnotherWorkstationIsRefusedAndNothingIsWritten() {
    var request = ContainerBindingRequest.container("web", 80);
    assertThatThrownBy(() -> bindings.add("away", "shop", request))
        .isInstanceOf(RemoteServiceException.class);
    assertThatThrownBy(() -> bindings.edit("away", "shop", "web:80", request))
        .isInstanceOf(RemoteServiceException.class);
    assertThatThrownBy(() -> bindings.remove("away", "shop", "web:80"))
        .isInstanceOf(RemoteServiceException.class);
    assertNothingWritten();
  }

  @Test
  void aContainerBindingNeedsAServiceThatThisWorkstationHas() {
    assertThatThrownBy(
            () -> bindings.add("home", "shop", ContainerBindingRequest.container("web", 80)))
        .isInstanceOf(UnknownServiceException.class);
    assertThatThrownBy(
            () -> bindings.add("home", BAD_NAME, ContainerBindingRequest.container("web", 80)))
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessageContaining(BAD_NAME);
    assertNothingWritten();
  }

  @Test
  void editingABindingGivesItsNewIdAndMovesItsOrderPlace() {
    services.create("home", "shop");
    String id = bindings.add("home", "shop", ContainerBindingRequest.container("web", 80));

    String edited = bindings.edit("home", "shop", id, ContainerBindingRequest.container("web", 90));

    assertThat(edited).isEqualTo("web:90").isNotEqualTo(id);
    assertThat(bindings.all(null).get("shop")).containsOnlyKeys("web:90");
    assertThat(
            bindings.edit("home", "shop", "web:90", ContainerBindingRequest.container("web", 90)))
        .as("an edit that changes nothing answers the same id")
        .isEqualTo("web:90");
  }

  private static PublicationRequest publicationRequest() {
    return new PublicationRequest(Scope.LOCAL, null, null, null);
  }
}
