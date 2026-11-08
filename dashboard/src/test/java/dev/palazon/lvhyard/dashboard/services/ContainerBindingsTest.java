package dev.palazon.lvhyard.dashboard.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The input rules of a Container Binding, before anything reads Docker or Consul. */
class ContainerBindingsTest {

  @Test
  void containerNameDockerWouldRefuseIsRefused() {
    assertRefused(
        new ContainerBindingRequest("my web", null, null, 80, null), "container", "my web");
  }

  @Test
  void containerPortIsRequired() {
    assertRefused(
        new ContainerBindingRequest("web", null, null, null, null),
        "containerPort",
        "needs a port");
  }

  @Test
  void bindingNeedsAContainerOrAComposeService() {
    assertRefused(
        new ContainerBindingRequest(null, null, null, 80, null),
        "container",
        "a Compose project and service");
  }

  @Test
  void bindingToAContainerAndAComposeServiceAtOnceIsRefused() {
    assertRefused(
        new ContainerBindingRequest("web", "shop", "web", 80, null), "container", "not both");
  }

  @Test
  void composeServiceNeedsItsProject() {
    assertRefused(
        new ContainerBindingRequest(null, null, "web", 80, null), "composeProject", "its project");
  }

  @Test
  void composeProjectNameComposeWouldRefuseIsRefused() {
    assertRefused(
        new ContainerBindingRequest(null, "My Shop", "web", 80, null), "composeProject", "My Shop");
  }

  private static void assertRefused(ContainerBindingRequest request, String field, String reason) {
    assertThatThrownBy(() -> ContainerBindings.check("web", request))
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessageContaining(reason)
        .extracting("field")
        .isEqualTo(field);
  }
}
