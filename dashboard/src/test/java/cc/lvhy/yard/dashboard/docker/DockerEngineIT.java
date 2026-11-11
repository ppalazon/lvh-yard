package cc.lvhy.yard.dashboard.docker;

import static org.awaitility.Awaitility.await;

import cc.lvhy.yard.dashboard.docker.FakeDocker.Spec;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HealthCheck;
import com.github.dockerjava.api.model.Ports;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/** The Docker contract against the real Docker that the tests run on. */
class DockerEngineIT extends DockerContract {

  /** Busybox has {@code nc}, and exposes no port of its own. */
  private static final DockerImageName BUSYBOX = DockerImageName.parse("busybox:1.37");

  private final Docker docker =
      new DockerEngine(
          new DockerSettings(
              URI.create(
                  DockerClientFactory.instance().getTransportConfig().getDockerHost().toString())));

  private final List<GenericContainer<?>> containers = new ArrayList<>();

  @AfterEach
  void removeContainers() {
    containers.forEach(GenericContainer::stop);
  }

  @Override
  Docker docker() {
    return docker;
  }

  @Override
  void start(Spec spec) {
    GenericContainer<?> container =
        new GenericContainer<>(BUSYBOX)
            .withCreateContainerCmdModifier(command -> {
              command
                  .withName(spec.name())
                  .withEntrypoint("sh", "-c", "while true; do nc -l -p 80 >/dev/null; done")
                  .withCmd()
                  .withExposedPorts(
                      Stream.concat(spec.ports().stream(), spec.publishedPorts().keySet().stream())
                          .map(ExposedPort::tcp)
                          .toList());
              Ports bindings = new Ports();
              for (Map.Entry<Integer, String> published : spec.publishedPorts().entrySet()) {
                String ip = published.getValue();
                bindings.bind(
                    ExposedPort.tcp(published.getKey()),
                    ip.contains(",") ? Ports.Binding.empty() : Ports.Binding.bindIp(ip));
              }
              command.getHostConfig().withPortBindings(bindings);
              if (spec.onHostNetwork()) {
                command.getHostConfig().withNetworkMode("host");
              }
              if (spec.healthState() != null) {
                command.withHealthcheck(
                    new HealthCheck()
                        .withTest(List.of("CMD-SHELL", "true"))
                        .withInterval(Duration.ofMillis(500).toNanos())
                        .withStartInterval(Duration.ofMillis(500).toNanos()));
              }
            });
    spec.labels().forEach(container::withLabel);
    containers.add(container);
    container.start();
    await()
        .atMost(Duration.ofSeconds(10))
        .until(() ->
            docker.runningContainers().stream().anyMatch(listed -> listed.isNamed(spec.name())));
  }

  @Override
  void stop(String name) {
    GenericContainer<?> container =
        containers.stream()
            .filter(started -> started.getContainerName().equals("/" + name))
            .findFirst()
            .orElseThrow();
    DockerClientFactory.instance()
        .client()
        .stopContainerCmd(container.getContainerId())
        .withTimeout(1)
        .exec();
  }
}
