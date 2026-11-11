package cc.lvhy.yard.dashboard;

import static org.awaitility.Awaitility.await;

import cc.lvhy.yard.dashboard.consul.FakeConsul;
import cc.lvhy.yard.dashboard.consul.FakeConsul.Datacenter;
import cc.lvhy.yard.dashboard.docker.FakeDocker;
import java.nio.file.Path;
import java.time.Duration;
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
      "lvh-yard.bound-instances.check-interval=1s",
      "lvh-yard.events.heartbeat-interval=1s"
    })
@Import(DashboardComponentTest.Fakes.class)
abstract class DashboardComponentTest extends DashboardClient {

  /** The Backups folder of this JVM's component tests. */
  static final Path BACKUPS = TestcontainersConfiguration.temporaryFolder("lvh-yard-component");

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
    boolean wasPaused = remote.reset();
    consul.restore(baseline);
    TestcontainersConfiguration.empty(BACKUPS);
    if (wasPaused) {
      // The dashboard skips a Workstation that failed for a while: wait until it reads it again.
      await()
          .atMost(Duration.ofSeconds(30))
          .until(() ->
              get("/api/yard")
                  .getBody()
                  .get("remotes")
                  .valueStream()
                  .noneMatch(workstation -> workstation.get("unreachable").asBoolean()));
    }
  }

  @Override
  void removeAllRouteProfiles() {
    consul.values(null, "lvh-yard/route-profiles/").keySet().forEach(consul::deleteValue);
  }

  /** This Workstation's LAN address, as its Consul agent gives it. */
  String lanAddress() {
    return FakeConsul.LAN_ADDRESS;
  }

  /** Something accepts TCP connections on {@code port} at the LAN address, so its checks pass. */
  void listenOnLanAddress(int port) {
    consul.listen(lanAddress(), port);
  }

  void stopListeningOnLanAddress(int port) {
    consul.stopListening(lanAddress(), port);
  }

  /** HTTP requests on {@code port} at the LAN address get {@code status} and {@code body}. */
  void answerHttpOnLanAddress(int port, String status, String body) {
    consul.answerHttp(lanAddress(), port, status, body);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Fakes {

    @Bean
    @Primary
    FakeConsul fakeConsul(FakeDocker docker) {
      FakeConsul consul = new FakeConsul(DashboardClient.WORKSTATION);
      // A running fake container accepts connections, so its Bound Instance passes.
      consul.alsoListening((host, port) -> docker.listensAt(host, port, FakeConsul.LAN_ADDRESS));
      return consul;
    }

    @Bean
    @Primary
    FakeDocker fakeDocker() {
      return new FakeDocker();
    }

    /** The other Workstation: its own dashboard, on the same fakes. */
    @Bean(destroyMethod = "close")
    RemoteWorkstation remoteWorkstation(FakeConsul consul, FakeDocker docker) {
      return new RemoteWorkstation(consul, docker);
    }

    @Bean
    DynamicPropertyRegistrar folderAndPorts() {
      return registry -> {
        registry.add("lvh-yard.backups.path", BACKUPS::toString);
        registry.add("lvh-yard.traefik.port", () -> TestcontainersConfiguration.TRAEFIK_PORT);
        registry.add(
            "lvh-yard.traefik.admin-port", () -> TestcontainersConfiguration.TRAEFIK_ADMIN_PORT);
      };
    }
  }
}
