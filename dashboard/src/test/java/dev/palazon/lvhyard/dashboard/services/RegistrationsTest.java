package dev.palazon.lvhyard.dashboard.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The input rules of a Static Instance: its Service name, its port and its health check. */
class RegistrationsTest {

  @Test
  void serviceNameThatIsNotADnsLabelIsRefused() {
    assertThatThrownBy(() -> Registrations.checkName("my_app"))
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessageContaining("my_app")
        .hasMessageContaining("not a single DNS label")
        .extracting("field")
        .isEqualTo("name");
  }

  @Test
  void portIsRequired() {
    assertThatThrownBy(() -> Registrations.checkPort(null))
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessage("A Static Instance needs a port.")
        .extracting("field")
        .isEqualTo("port");
  }

  @Test
  void scriptCheckIsRefused() {
    assertRefused(new HealthCheck("script", null, null, null), "check", "Script checks");
  }

  @Test
  void intervalUnderOneSecondIsRefused() {
    assertRefused(new HealthCheck(null, null, "500ms", null), "interval", "once a second");
  }

  @Test
  void timeoutThatIsNotADurationIsRefused() {
    assertRefused(new HealthCheck(null, null, null, "2"), "timeout", "not a duration");
  }

  @Test
  void tcpCheckWithAPathIsRefused() {
    assertRefused(new HealthCheck("tcp", "/health", null, null), "path", "A TCP check has no path");
  }

  private static void assertRefused(HealthCheck check, String field, String reason) {
    assertThatThrownBy(() -> Registrations.check(check))
        .isInstanceOf(InvalidRegistrationException.class)
        .hasMessageContaining(reason)
        .extracting("field")
        .isEqualTo(field);
  }
}
