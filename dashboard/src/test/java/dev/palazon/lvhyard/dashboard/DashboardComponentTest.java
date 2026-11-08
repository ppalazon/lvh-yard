package dev.palazon.lvhyard.dashboard;

import static org.awaitility.Awaitility.await;

import dev.palazon.lvhyard.dashboard.consul.FakeConsul;
import dev.palazon.lvhyard.dashboard.consul.FakeConsul.Datacenter;
import dev.palazon.lvhyard.dashboard.docker.FakeDocker;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * The dashboard with {@link FakeConsul} and {@link FakeDocker} behind it, and no real system. Both
 * fakes go back to their state after the first setup before each test.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "lvh-yard.workstation.name=" + DashboardClient.WORKSTATION,
      "lvh-yard.workstation.base-domain=" + DashboardClient.BASE_DOMAIN,
      "lvh-yard.consul.peers=",
      // Nothing answers at these addresses: the fakes stand in for Consul and Docker.
      "lvh-yard.consul.url=http://127.0.0.1:1",
      "lvh-yard.docker.host=unix:///fake/docker.sock",
      "lvh-yard.consul.answer-timeout=1s",
      "lvh-yard.consul.unreachable-pause=2s",
      "lvh-yard.events.heartbeat-interval=1s"
    })
@Import(DashboardComponentTest.Fakes.class)
abstract class DashboardComponentTest extends DashboardClient {

  /** The Backups folder of this JVM's component tests. */
  static final Path BACKUPS = TestcontainersConfiguration.temporaryFolder("lvh-yard-component");

  /** Two free ports for Traefik, where nothing listens. */
  private static final int[] TRAEFIK_PORTS = TestcontainersConfiguration.freePorts(2);

  /** This Workstation's Consul after the first setup, which each test starts from. */
  private static volatile Map<String, Datacenter> baseline;

  @Autowired FakeConsul consul;

  @BeforeEach
  void resetFakes() {
    docker.reset();
    if (baseline == null) {
      // The dashboard does its first setup in the background, after it has started.
      await().until(() -> !consul.values(null, "lvh-yard/first-setup").isEmpty());
      baseline = consul.snapshot();
    }
    consul.restore(baseline);
    TestcontainersConfiguration.empty(BACKUPS);
  }

  @Override
  void removeAllRouteProfiles() {
    consul.values(null, "lvh-yard/route-profiles/").keySet().forEach(consul::deleteValue);
  }

  /** The other Workstation's datacenter in {@link FakeConsul}. */
  Datacenter remoteDatacenter() {
    return consul.datacenter(RemoteWorkstation.NAME);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Fakes {

    @Bean
    @Primary
    FakeConsul fakeConsul() {
      return new FakeConsul(DashboardClient.WORKSTATION);
    }

    @Bean
    @Primary
    FakeDocker fakeDocker() {
      return new FakeDocker();
    }

    @Bean
    DynamicPropertyRegistrar folderAndPorts() {
      return registry -> {
        registry.add("lvh-yard.backups.path", BACKUPS::toString);
        registry.add("lvh-yard.traefik.port", () -> TRAEFIK_PORTS[0]);
        registry.add("lvh-yard.traefik.admin-port", () -> TRAEFIK_PORTS[1]);
      };
    }
  }
}
