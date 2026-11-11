package cc.lvhy.yard.dashboard.docker;

import cc.lvhy.yard.dashboard.docker.FakeDocker.Spec;

class FakeDockerTest extends DockerContract {

  private final FakeDocker docker = new FakeDocker();

  @Override
  Docker docker() {
    return docker;
  }

  @Override
  void start(Spec spec) {
    docker.start(spec);
  }

  @Override
  void stop(String name) {
    docker.stop(name);
  }
}
