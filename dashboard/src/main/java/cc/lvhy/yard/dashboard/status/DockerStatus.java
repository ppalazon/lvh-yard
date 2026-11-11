package cc.lvhy.yard.dashboard.status;

import cc.lvhy.yard.dashboard.docker.Docker;
import cc.lvhy.yard.dashboard.docker.DockerFailures;
import cc.lvhy.yard.dashboard.docker.DockerSettings;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(StatusOrder.DOCKER)
class DockerStatus implements PartReader {

  static final String NAME = "Docker";

  private final Docker docker;

  private final DockerSettings settings;

  private final DockerFailures failures;

  DockerStatus(Docker docker, DockerSettings settings, DockerFailures failures) {
    this.docker = docker;
    this.settings = settings;
    this.failures = failures;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Part read() {
    Docker.Version version;
    try {
      docker.ping();
      version = docker.version();
    } catch (RuntimeException e) {
      return Part.failing(NAME, failures.reason(e));
    }
    return Part.of(
        NAME,
        "Docker answers.",
        List.of(
            new Part.Detail("ping", "OK", PartState.OK),
            Part.Detail.fact("version", version.version()),
            Part.Detail.fact("API version", version.apiVersion()),
            Part.Detail.fact("host", settings.host().toString())));
  }
}
