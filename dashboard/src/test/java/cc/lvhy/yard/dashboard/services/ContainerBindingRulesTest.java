package cc.lvhy.yard.dashboard.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The input rules of a Container Binding, before anything reads Docker or Consul. */
class ContainerBindingRulesTest {

  @Test
  void containerNameDockerWouldRefuseIsRefused() {
    assertRefused(new ContainerBindingRequest("my web", null, null, 80), "container", "my web");
  }

  @Test
  void containerPortIsRequired() {
    assertRefused(
        new ContainerBindingRequest("web", null, null, null), "containerPort", "needs a port");
  }

  @Test
  void bindingNeedsAContainerOrAComposeService() {
    assertRefused(
        new ContainerBindingRequest(null, null, null, 80),
        "container",
        "a Compose project and service");
  }

  @Test
  void bindingToAContainerAndAComposeServiceAtOnceIsRefused() {
    assertRefused(new ContainerBindingRequest("web", "shop", "web", 80), "container", "not both");
  }

  @Test
  void composeServiceNeedsItsProject() {
    assertRefused(
        new ContainerBindingRequest(null, null, "web", 80), "composeProject", "its project");
  }

  @Test
  void composeProjectNameComposeWouldRefuseIsRefused() {
    assertRefused(
        new ContainerBindingRequest(null, "My Shop", "web", 80), "composeProject", "My Shop");
  }

  @Test
  void aTargetIsTheContainerNameOrTheComposeProjectAndServiceWhateverThePort() {
    ContainerBindingRequest web80 = ContainerBindingRequest.container("web", 80);
    ContainerBindingRequest web90 = ContainerBindingRequest.container("web", 90);
    ContainerBindingRequest db = ContainerBindingRequest.container("db", 80);
    ContainerBindingRequest shopWeb = ContainerBindingRequest.compose("shop", "web", 80);
    ContainerBindingRequest shopWeb90 = ContainerBindingRequest.compose("shop", "web", 90);
    ContainerBindingRequest shopDb = ContainerBindingRequest.compose("shop", "db", 80);
    ContainerBindingRequest otherWeb = ContainerBindingRequest.compose("other", "web", 80);

    assertThat(ContainerBindingRules.sameTarget(web80, web90)).isTrue();
    assertThat(ContainerBindingRules.sameTarget(web80, db)).isFalse();
    assertThat(ContainerBindingRules.sameTarget(shopWeb, shopWeb90)).isTrue();
    assertThat(ContainerBindingRules.sameTarget(shopWeb, shopDb)).isFalse();
    assertThat(ContainerBindingRules.sameTarget(shopWeb, otherWeb)).isFalse();
    assertThat(ContainerBindingRules.sameTarget(web80, shopWeb))
        .as("a name and a Compose service are different targets")
        .isFalse();
  }

  private static void assertRefused(ContainerBindingRequest request, String field, String reason) {
    assertThatThrownBy(() -> ContainerBindingRules.check("web", request))
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessageContaining(reason)
        .extracting("field")
        .isEqualTo(field);
  }
}
