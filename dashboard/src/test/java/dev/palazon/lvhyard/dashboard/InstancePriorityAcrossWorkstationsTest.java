package dev.palazon.lvhyard.dashboard;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The Instance Priority of a Service with Yard Scope, as another Workstation applies it. The remote
 * Workstation here counts only loopback as out of range, as the test Yard's LAN addresses are
 * container addresses, which are out of range by default.
 */
class InstancePriorityAcrossWorkstationsTest extends InstancePriorityTestSupport {

  @Test
  void yardScopedServiceIsRoutedToTheSameKindOnEveryWorkstation() {
    String web = newServiceName("web");
    String terminal = staticInstance(web);
    boundInstance(web);

    put(publicationPath(web), Map.of("scope", "yard"));

    awaitRoutedTo(web, terminal);
    onRemoteThatReachesContainers(() -> awaitRoutedOnRemote(web, terminal));
  }

  @Test
  void yardScopedServiceIsRoutedWithItsHomeWorkstationsOrderOnEveryWorkstation() {
    String web = newServiceName("web");
    staticInstance(web);
    String bound = boundInstance(web);

    put(publicationPath(web), Map.of("scope", "yard", "priority", List.of("bound", "static")));

    awaitRoutedTo(web, bound);
    onRemoteThatReachesContainers(() -> awaitRoutedOnRemote(web, bound));
  }

  @Test
  void yardScopedServiceWhoseInstancePriorityIsOffIsOffOnEveryWorkstationWithoutOutOfRangeOnes() {
    String web = newServiceName("web");
    String terminal = staticInstance(web);
    String bound = boundInstance(web);
    int port = newLanPort();
    listenOnLanAddress(port);
    register(web, Map.of("host", "127.0.0.1", "port", port, "check", Map.of("interval", "1s")));

    put(publicationPath(web), Map.of("scope", "yard", "priority", List.of()));

    awaitRoutedTo(web, terminal, bound, "127.0.0.1:" + port);
    onRemoteThatReachesContainers(() -> awaitRoutedOnRemote(web, terminal, bound));
  }

  @Test
  void otherWorkstationsSkipAnOutOfRangeInstanceBeforeChoosingAKind() {
    String web = newServiceName("web");
    int port = newLanPort();
    listenOnLanAddress(port);
    register(web, Map.of("host", "127.0.0.1", "port", port, "check", Map.of("interval", "1s")));
    String bound = boundInstance(web);

    put(publicationPath(web), Map.of("scope", "yard"));

    awaitRoutedTo(web, "127.0.0.1:" + port);
    onRemoteThatReachesContainers(() -> awaitRoutedOnRemote(web, bound));
  }

  private void onRemoteThatReachesContainers(Runnable meanwhile) {
    remote.whileDashboardRunsWith(meanwhile, "--lvh-yard.out-of-range.ranges=127.0.0.0/8,::1/128");
  }

  private void awaitRoutedOnRemote(String service, String... addresses) {
    awaitRoutedTo(
        () -> remote.traefikConfiguration().get("http"),
        service + "." + WORKSTATION + ".lvh.test",
        addresses);
  }
}
