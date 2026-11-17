package cc.lvhy.yard.dashboard.services;

import static org.assertj.core.api.Assertions.assertThat;

import cc.lvhy.yard.dashboard.profiles.RouteProfileDefinition;
import cc.lvhy.yard.dashboard.services.ServiceWarning.Kind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ServiceWarningTest {

  private static final Map<String, RouteProfileDefinition> PROFILES =
      Map.of(
          "cert",
          new RouteProfileDefinition("cert", Map.of("cert", Map.of("clientCertificate", Map.of()))),
          "mtls",
          new RouteProfileDefinition("mtls", Map.of("mtls", Map.of("mutualTLS", Map.of()))));

  private static List<ServiceWarning> warnings(
      String profile, boolean noHttps, boolean noClientAuth, boolean noMtls) {
    return ServiceWarning.of(
            new Publication(Scope.YARD, List.of(profile), false),
            List.of(),
            List.of(),
            true,
            PROFILES,
            noHttps,
            noClientAuth,
            noMtls,
            false)
        .stream()
        .filter(warning -> warning.kind() != Kind.ROUTE_PROFILE_AUTH)
        .toList();
  }

  @Test
  void clientCertificateNamesWhatIsMissing() {
    assertThat(warnings("cert", true, false, false))
        .singleElement()
        .satisfies(w -> {
          assertThat(w.kind()).isEqualTo(Kind.CLIENT_CERTIFICATE_UNAVAILABLE);
          assertThat(w.message()).contains("HTTPS");
        });
    assertThat(warnings("cert", false, true, false))
        .singleElement()
        .satisfies(w -> assertThat(w.message()).contains("client authentication"));
    assertThat(warnings("cert", false, false, true)).isEmpty();
  }

  @Test
  void mutualTlsWarnsOnlyWithoutMtlsAndDoesNotAskWhoTheUserIs() {
    assertThat(warnings("mtls", false, false, false)).isEmpty();
    assertThat(warnings("mtls", true, true, true))
        .singleElement()
        .satisfies(w -> assertThat(w.kind()).isEqualTo(Kind.MUTUAL_TLS_UNAVAILABLE));
  }

  private static Instance instance(String host) {
    return new Instance(
        "echo-1",
        host,
        8443,
        Health.PASSING,
        null,
        List.of(),
        Map.of(),
        List.of(),
        false,
        null,
        null,
        null,
        null,
        false,
        false);
  }

  private static List<ServiceWarning> unencrypted(
      String profile, boolean httpsAtHome, String... hosts) {
    return ServiceWarning.of(
            new Publication(Scope.YARD, List.of(profile), false),
            java.util.Arrays.stream(hosts).map(ServiceWarningTest::instance).toList(),
            List.of(),
            true,
            PROFILES,
            false,
            false,
            false,
            httpsAtHome)
        .stream()
        .filter(warning -> warning.kind() == Kind.UNENCRYPTED_INSTANCE)
        .toList();
  }

  @Test
  void unencryptedHopWarnsOnlyForHttpsInstancesOffLoopbackAndWithoutMutualTls() {
    assertThat(unencrypted("cert", true, "192.168.1.5", "127.0.0.1"))
        .singleElement()
        .satisfies(w -> {
          assertThat(w.severity()).isEqualTo(ServiceWarning.Severity.RISK);
          assertThat(w.message()).contains("192.168.1.5:8443").doesNotContain("127.0.0.1");
        });
    assertThat(unencrypted("cert", false, "192.168.1.5")).isEmpty();
    assertThat(unencrypted("cert", true, "127.0.0.1", "localhost")).isEmpty();
    assertThat(unencrypted("mtls", true, "192.168.1.5")).isEmpty();
  }
}
