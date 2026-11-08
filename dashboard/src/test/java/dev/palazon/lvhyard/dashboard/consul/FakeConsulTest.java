package dev.palazon.lvhyard.dashboard.consul;

class FakeConsulTest extends ConsulContract {

  private final FakeConsul consul = new FakeConsul(DATACENTER);

  @Override
  Consul consul() {
    return consul;
  }

  @Override
  String listening() {
    consul.listen("10.1.0.1", 80);
    return "10.1.0.1:80";
  }

  @Override
  String silent() {
    return "10.1.0.2:80";
  }

  @Override
  String answeringHttp(String statusLine, String body) {
    int port = 8000 + Math.abs(statusLine.hashCode() % 1000);
    consul.answerHttp("10.1.0.3", port, statusLine, body);
    return "10.1.0.3:" + port;
  }
}
