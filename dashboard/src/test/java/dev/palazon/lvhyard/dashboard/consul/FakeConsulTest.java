package dev.palazon.lvhyard.dashboard.consul;

class FakeConsulTest extends ConsulContract {

  private final FakeConsul consul = new FakeConsul(DATACENTER);

  @Override
  Consul consul() {
    return consul;
  }
}
