package dev.palazon.lvhyard.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class RegisterTest extends YardTestSupport {

  @Test
  void registerStaticAddsStaticInstanceAtHostAndPort() {
    dashboard.answer(
        "POST",
        "/api/workstation/services/web/instances",
        201,
        """
        {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "critical",
         "tags": [], "meta": {}, "checks": [], "outOfRange": false}
        """);

    Run run = yard("register", "static", "web", "--host", "10.1.2.3", "--port", "8080");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Registered web at 10.1.2.3:8080, Instance 7b1c.\n");
    dashboard.verifyCalled(
        "POST",
        "/api/workstation/services/web/instances",
        """
        {"host": "10.1.2.3", "port": 8080}
        """);
  }

  @Test
  void registerStaticWithoutHostLeavesItToTheDashboard() {
    dashboard.answer(
        "POST",
        "/api/workstation/services/web/instances",
        201,
        """
        {"id": "7b1c", "host": "192.168.1.20", "port": 8080, "health": "critical",
         "tags": [], "meta": {}, "checks": [], "outOfRange": false}
        """);

    Run run = yard("register", "static", "web", "--port", "8080");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).contains("at 192.168.1.20:8080");
    dashboard.verifyCalled(
        "POST",
        "/api/workstation/services/web/instances",
        """
        {"port": 8080}
        """);
  }

  @Test
  void registerStaticTakesTheCheckItsIntervalAndTimeoutTagsAndMeta() {
    dashboard.answer(
        "POST",
        "/api/workstation/services/web/instances",
        201,
        """
        {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "critical",
         "check": {"type": "http", "path": "/health", "interval": "5s", "timeout": "1s"},
         "tags": ["backend", "v2"], "meta": {"owner": "pablo", "team": "core"},
         "checks": [{"name": "HTTP GET /health", "health": "critical", "output": ""}],
         "outOfRange": false}
        """);

    Run run =
        yard(
            "register",
            "static",
            "web",
            "--port",
            "8080",
            "--check",
            "http",
            "--path",
            "/health",
            "--interval",
            "5s",
            "--timeout",
            "1s",
            "--tag",
            "backend",
            "--tag",
            "v2",
            "--meta",
            "owner=pablo",
            "--meta",
            "team=core");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Registered web at 10.1.2.3:8080, Instance 7b1c.\n");
    dashboard.verifyCalled(
        "POST",
        "/api/workstation/services/web/instances",
        """
        {"port": 8080, "check": {"type": "http", "path": "/health", "interval": "5s", "timeout": "1s"},
         "tags": ["backend", "v2"], "meta": {"owner": "pablo", "team": "core"}}
        """);
  }

  @Test
  void registerStaticWithOnlyAPathLeavesTheRestOfTheCheckToTheDashboard() {
    dashboard.answer(
        "POST",
        "/api/workstation/services/web/instances",
        201,
        """
        {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "critical",
         "check": {"type": "http", "path": "/health", "interval": "10s", "timeout": "2s"},
         "tags": [], "meta": {}, "checks": [], "outOfRange": false}
        """);

    Run run = yard("register", "static", "web", "--port", "8080", "--path", "/health");

    assertThat(run.exitCode()).isZero();
    dashboard.verifyCalled(
        "POST",
        "/api/workstation/services/web/instances",
        """
        {"port": 8080, "check": {"path": "/health"}}
        """);
  }

  @Test
  void registerStaticOnAMissingServiceNamesServiceCreate() {
    answerNoService("POST", "/api/workstation/services/web/instances", "web");

    Run run = yard("register", "static", "web", "--port", "8080");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err())
        .isEqualTo(
            "This Workstation has no Service web. Create it first: lvh-yard service create"
                + " web\n");
  }

  @Test
  void registerContainerOnAMissingServiceNamesServiceCreate() {
    answerNoService("POST", "/api/workstation/services/web/container-bindings", "web");

    Run run = yard("register", "container", "grafana", "--service", "web", "--port", "3000");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).contains("lvh-yard service create web");
  }

  @Test
  void registerContainerOnAMissingServiceOfTheContainerNameNamesServiceCreate() {
    answerNoService("POST", "/api/workstation/services/grafana/container-bindings", "grafana");

    Run run = yard("register", "container", "grafana", "--port", "3000");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).contains("lvh-yard service create grafana");
  }

  @Test
  void registerComposeOnAMissingServiceNamesServiceCreate() {
    answerNoService("POST", "/api/workstation/services/web/container-bindings", "web");

    Run run = yard("register", "compose", "shop/web", "--port", "80");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).contains("lvh-yard service create web");
  }

  @Test
  void registerHasNoPublishOption() {
    Run run = yard("register", "static", "web", "--port", "8080", "--no-publish");

    assertThat(run.exitCode()).isEqualTo(2);
    assertThat(run.err()).contains("--no-publish");
  }

  private void answerNoService(String method, String path, String service) {
    dashboard.answer(
        method,
        path,
        404,
        """
        {"type": "about:blank", "title": "Not Found", "status": 404,
         "detail": "This Workstation has no Service %s.", "service": "%s"}
        """
            .formatted(service, service));
  }

  @Test
  void registerStaticWithAScriptCheckShowsWhyTheDashboardRefused() {
    dashboard.answer(
        "POST",
        "/api/workstation/services/web/instances",
        400,
        """
        {"status": 400, "detail": "Script checks are not offered: use an http or a tcp check."}
        """);

    Run run = yard("register", "static", "web", "--port", "8080", "--check", "script");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).contains("Script checks are not offered");
  }

  /** The container list with one running container called {@code name} on {@code ports}. */
  private void answerContainer(String name, String ports) {
    dashboard.answer(
        "GET",
        "/api/workstation/containers",
        200,
        """
        [{"name": "%s", "service": "%s", "running": true, "ports": [%s]}]
        """
            .formatted(name, name, ports));
  }

  /** The Binding that the dashboard answers for a bound container. */
  private void answerContainerBinding(String service, String container, int port) {
    dashboard.answer(
        "POST",
        "/api/workstation/services/" + service + "/container-bindings",
        201,
        """
        {"id": "%s:%d", "container": "%s", "port": %d, "instances": []}
        """
            .formatted(container, port, container, port));
  }

  @Test
  void registerContainerBindsTheOnlyPortAndSaysWhereItIsPublished() {
    answerContainer("grafana", "{\"containerPort\": 3000}");
    answerContainerBinding("grafana", "grafana", 3000);
    dashboard.answer(
        "GET",
        "/api/workstation/services/grafana",
        200,
        """
        {"workstation": "ws-a", "name": "grafana", "state": "healthy", "created": true, "empty": false, "sources": [], "instancePriority": true,
         "publication": {"scope": "local", "accessLog": false}, "hostname": "grafana.lvh.me",
         "containerBindings": [], "warnings": [],
         "instances": [{"id": "grafana:grafana:3000", "host": "172.17.0.2", "port": 3000,
                        "health": "passing", "outOfRange": false,
                        "checks": [], "tags": [], "meta": {}}]}
        """);

    Run run = yard("register", "container", "grafana");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo("Registered grafana as grafana, published at grafana.lvh.me.\n");
    dashboard.verifyCalled(
        "POST",
        "/api/workstation/services/grafana/container-bindings",
        """
        {"container": "grafana", "port": 3000}
        """);
  }

  @Test
  void registerContainerSendsThePortAndTheService() {
    answerContainerBinding("shop", "storefront", 9000);
    dashboard.answer(
        "GET",
        "/api/workstation/services/shop",
        200,
        """
        {"workstation": "ws-a", "name": "shop", "state": "healthy", "created": true, "empty": false, "sources": [], "instancePriority": true,
         "containerBindings": [], "warnings": [],
         "instances": [{"id": "shop:storefront:9000", "host": "172.17.0.2", "port": 9000,
                        "health": "passing", "outOfRange": false,
                        "checks": [], "tags": [], "meta": {}}]}
        """);

    Run run = yard("register", "container", "storefront", "--port", "9000", "--service", "shop");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Registered storefront as shop. shop is not published.\n");
    dashboard.verifyCalled(
        "POST",
        "/api/workstation/services/shop/container-bindings",
        """
        {"container": "storefront", "port": 9000}
        """);
  }

  @Test
  void registerContainerUsesTheCleanedContainerNameAsTheService() {
    answerContainerBinding("my-web", "My_Web", 80);
    dashboard.answer(
        "GET",
        "/api/workstation/services/my-web",
        200,
        """
        {"workstation": "ws-a", "name": "my-web", "state": "unpublished", "created": true,
         "empty": false, "sources": [], "instancePriority": true, "containerBindings": [], "warnings": [], "instances": []}
        """);

    Run run = yard("register", "container", "My_Web", "--port", "80");

    assertThat(run.exitCode()).isZero();
    dashboard.verifyCalled(
        "POST",
        "/api/workstation/services/my-web/container-bindings",
        """
        {"container": "My_Web", "port": 80}
        """);
  }

  @Test
  void registerContainerOfAContainerThatDoesNotRunSaysWhenItGetsItsInstance() {
    answerContainerBinding("catalog", "catalog", 80);
    dashboard.answer(
        "GET",
        "/api/workstation/services/catalog",
        200,
        """
        {"workstation": "ws-a", "name": "catalog", "state": "unavailable", "created": true, "empty": false, "sources": [], "instancePriority": true,
         "publication": {"scope": "local", "accessLog": false}, "hostname": "catalog.lvh.me",
         "containerBindings": [], "warnings": [], "instances": []}
        """);

    Run run = yard("register", "container", "catalog", "--port", "80");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            "Registered catalog as catalog, published at catalog.lvh.me. No container"
                + " of that name runs now: catalog gets its Bound Instance once one starts.\n");
  }

  @Test
  void registerContainerWithoutAPortOfAContainerThatIsNotListedSaysToGiveIt() {
    dashboard.answer("GET", "/api/workstation/containers", 200, "[]");

    Run run = yard("register", "container", "catalog");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.err()).contains("catalog").contains("--port");
    dashboard.verifyNotCalled("POST", "/api/workstation/services/catalog/container-bindings");
  }

  @Test
  void registerContainerWithoutAPortOnAContainerWithSeveralListsThePorts() {
    answerContainer("storefront", "{\"containerPort\": 80}, {\"containerPort\": 9000}");

    Run run = yard("register", "container", "storefront");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.err()).contains("80, 9000").contains("--port");
  }

  @Test
  void registerContainerOfAFollowedContainerSaysWhichServiceFollowsIt() {
    dashboard.answer(
        "POST",
        "/api/workstation/services/grafana/container-bindings",
        409,
        """
        {"status": 409,
         "detail": "The container grafana is already followed by a Container Binding of the Service metrics."}
        """);

    Run run = yard("register", "container", "grafana", "--port", "3000");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.err())
        .contains("already followed by a Container Binding of the Service metrics.");
  }

  @Test
  void registerContainerPrintsTheServicesWarnings() {
    answerContainerBinding("web", "web-1", 80);
    dashboard.answer(
        "GET",
        "/api/workstation/services/web",
        200,
        """
        {"workstation": "ws-a", "name": "web", "state": "healthy", "created": true, "empty": false, "sources": [], "instancePriority": true,
         "publication": {"scope": "yard", "accessLog": false}, "hostname": "web.lvh.me",
         "containerBindings": [], "warnings": [
            {"kind": "unpublished-container-port", "severity": "risk",
             "message": "The Bound Instance at 172.17.0.2:80 is on a container port that is not published."}],
         "instances": [{"id": "web:web-1:80", "host": "172.17.0.2", "port": 80,
                        "health": "passing", "outOfRange": false,
                        "checks": [], "tags": [], "meta": {}}]}
        """);

    Run run = yard("register", "container", "web-1", "--service", "web", "--port", "80");

    assertThat(run.exitCode()).isZero();
    assertThat(run.err())
        .isEqualTo(
            "risk: The Bound Instance at 172.17.0.2:80 is on a container port that is not"
                + " published.\n");
  }

  @Test
  void registerComposeBindsTheComposeServiceIntoAServiceOfItsCleanedName() {
    dashboard.answer(
        "POST",
        "/api/workstation/services/my-web/container-bindings",
        201,
        """
        {"id": "compose:shop:my_web:80", "composeProject": "shop", "composeService": "my_web",
         "port": 80, "instances": ["my-web:storefront:80", "my-web:shop-web-2:80"]}
        """);

    Run run = yard("register", "compose", "shop/my_web", "--port", "80");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            "Bound my-web to Compose service my_web of project shop, port 80. 2 of its"
                + " containers run now, so my-web has 2 Bound Instances.\n");
    dashboard.verifyCalled(
        "POST",
        "/api/workstation/services/my-web/container-bindings",
        """
        {"composeProject": "shop", "composeService": "my_web", "port": 80}
        """);
  }

  @Test
  void registerComposeSendsTheService() {
    dashboard.answer(
        "POST",
        "/api/workstation/services/shop/container-bindings",
        201,
        """
        {"id": "compose:shop:web:80", "composeProject": "shop", "composeService": "web", "port": 80,
         "instances": []}
        """);

    Run run = yard("register", "compose", "shop/web", "--port", "80", "--service", "shop");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            "Bound shop to Compose service web of project shop, port 80. None of its"
                + " containers runs now: shop gets a Bound Instance for each one that starts.\n");
    dashboard.verifyCalled(
        "POST",
        "/api/workstation/services/shop/container-bindings",
        """
        {"composeProject": "shop", "composeService": "web", "port": 80}
        """);
  }

  @Test
  void registerComposeWithOneContainerReportsOneBoundInstance() {
    dashboard.answer(
        "POST",
        "/api/workstation/services/web/container-bindings",
        201,
        """
        {"id": "compose:shop:web:80", "composeProject": "shop", "composeService": "web",
         "port": 80, "instances": ["web:shop-web-1:80"]}
        """);

    Run run = yard("register", "compose", "shop/web", "--port", "80");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            "Bound web to Compose service web of project shop, port 80. 1 of its"
                + " containers runs now, so web has 1 Bound Instance.\n");
  }

  @Test
  void registerComposeCleansAndShortensTheDefaultServiceName() {
    String service = "a".repeat(62) + "-Extra";
    String name = "a".repeat(62);
    dashboard.answer(
        "POST",
        "/api/workstation/services/" + name + "/container-bindings",
        201,
        """
        {"id": "binding", "composeProject": "shop", "composeService": "%s",
         "port": 80, "instances": []}
        """
            .formatted(service));

    Run run = yard("register", "compose", "shop/" + service, "--port", "80");

    assertThat(run.exitCode()).isZero();
    dashboard.verifyCalled(
        "POST",
        "/api/workstation/services/" + name + "/container-bindings",
        """
        {"composeProject": "shop", "composeService": "%s", "port": 80}
        """
            .formatted(service));
  }

  @Test
  void registerComposeReportsDashboardErrors() {
    dashboard.answer(
        "POST",
        "/api/workstation/services/web/container-bindings",
        400,
        """
        {"status": 400, "detail": "The container port must be between 1 and 65535."}
        """);

    Run run = yard("register", "compose", "shop/web", "--port", "0");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.err()).contains("The container port must be between 1 and 65535.");
  }

  @Test
  void registerComposeNeedsAPort() {
    Run run = yard("register", "compose", "shop/web");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).contains("--port");
  }

  @Test
  void registerComposeNeedsAProjectAndAService() {
    for (String target : List.of("", "web", "/web", "shop/", "shop/web/extra")) {
      Run run = yard("register", "compose", target, "--port", "80");

      assertThat(run.exitCode()).as(target).isNotZero();
      assertThat(run.err()).contains("<project>/<service>");
    }
  }

  @Test
  void registerTargetsAreRequired() {
    assertThat(yard("register", "container").exitCode()).isNotZero();
    assertThat(yard("register", "compose", "--port", "80").exitCode()).isNotZero();
  }

  @Test
  void registerComposeThatABindingFollowsSaysWhichServiceFollowsIt() {
    dashboard.answer(
        "POST",
        "/api/workstation/services/web/container-bindings",
        409,
        """
        {"status": 409,
         "detail": "The Compose service web of project shop is already followed by a Container Binding of the Service shop."}
        """);

    Run run = yard("register", "compose", "shop/web", "--port", "80");

    assertThat(run.exitCode()).isEqualTo(1);
    assertThat(run.err()).contains("of the Service shop.");
  }

  @Test
  void registerWithAServiceAndNoSubcommandIsRefused() {
    Run run = yard("register", "web", "--port", "80");

    assertThat(run.exitCode()).isEqualTo(2);
    assertThat(run.err()).contains("web").contains("static").contains("container");
  }

  @Test
  void removeWithContainerRemovesThatContainerBinding() {
    answerWebWithBindings(
        """
        {"id": "web-1:80", "container": "web-1", "port": 80, "instances": []}\
        """);
    dashboard.answer(
        "DELETE", "/api/workstation/services/web/container-bindings/web-1%3A80", 204, null);

    Run run = yard("remove", "web", "--container", "web-1");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo("Removed the Container Binding of web to container web-1, port 80.\n");
    dashboard.verifyCalled("DELETE", "/api/workstation/services/web/container-bindings/web-1%3A80");
  }

  @Test
  void removeWithContainerAndPortPicksThatPort() {
    answerWebWithBindings(
        """
        {"id": "web-1:80", "container": "web-1", "port": 80, "instances": []},
        {"id": "web-1:443", "container": "web-1", "port": 443, "instances": []}\
        """);
    dashboard.answer(
        "DELETE", "/api/workstation/services/web/container-bindings/web-1%3A443", 204, null);

    Run run = yard("remove", "web", "--container", "web-1", "--port", "443");

    assertThat(run.exitCode()).isZero();
    dashboard.verifyCalled(
        "DELETE", "/api/workstation/services/web/container-bindings/web-1%3A443");
  }

  @Test
  void removeWithContainerBoundOnMoreThanOnePortAsksForThePort() {
    answerWebWithBindings(
        """
        {"id": "web-1:80", "container": "web-1", "port": 80, "instances": []},
        {"id": "web-1:443", "container": "web-1", "port": 443, "instances": []}\
        """);

    Run run = yard("remove", "web", "--container", "web-1");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err())
        .isEqualTo(
            "web is bound to container web-1 on more than one port (80, 443): give" + " --port.\n");
  }

  @Test
  void removeWithContainerThatIsNotBoundSaysSo() {
    answerWebWithBindings("");

    Run run = yard("remove", "web", "--container", "web-1");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).isEqualTo("web has no Container Binding to container web-1.\n");
  }

  @Test
  void removeWithComposeServiceRemovesThatContainerBinding() {
    answerWebWithBindings(
        """
        {"id": "web-1:80", "container": "web-1", "port": 80, "instances": []},
        {"id": "compose:shop:web:80", "composeProject": "shop", "composeService": "web", "port": 80,
         "instances": []}\
        """);
    dashboard.answer(
        "DELETE",
        "/api/workstation/services/web/container-bindings/compose%3Ashop%3Aweb%3A80",
        204,
        null);

    Run run = yard("remove", "web", "--compose-project", "shop", "--compose-service", "web");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            "Removed the Container Binding of web to Compose service web of project"
                + " shop, port 80.\n");
    dashboard.verifyCalled(
        "DELETE", "/api/workstation/services/web/container-bindings/compose%3Ashop%3Aweb%3A80");
  }

  @Test
  void removeWithComposeServiceThatIsNotBoundSaysSo() {
    answerWebWithBindings(
        """
        {"id": "web-1:80", "container": "web-1", "port": 80, "instances": []}\
        """);

    Run run = yard("remove", "web", "--compose-project", "shop", "--compose-service", "web");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err())
        .isEqualTo("web has no Container Binding to Compose service web of project shop.\n");
  }

  @Test
  void removeByIdRemovesThatInstance() {
    dashboard.answer("DELETE", "/api/workstation/services/web/instances/7b1c", 204, null);

    Run run = yard("remove", "web", "--id", "7b1c");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Removed Instance 7b1c of web.\n");
    dashboard.verifyCalled("DELETE", "/api/workstation/services/web/instances/7b1c");
  }

  @Test
  void removeByPortRemovesTheInstanceAtThatPort() {
    answerWebWithInstances(
        """
        {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "passing",
         "tags": [], "meta": {}, "checks": [], "outOfRange": false},
        {"id": "9d4e", "host": "10.1.2.3", "port": 8081, "health": "passing",
         "tags": [], "meta": {}, "checks": [], "outOfRange": false}\
        """);
    dashboard.answer("DELETE", "/api/workstation/services/web/instances/9d4e", 204, null);

    Run run = yard("remove", "web", "--port", "8081");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Removed web at 10.1.2.3:8081.\n");
    dashboard.verifyCalled("DELETE", "/api/workstation/services/web/instances/9d4e");
  }

  @Test
  void removeByHostAndPortPicksTheInstanceAtThatHost() {
    answerWebWithInstances(
        """
        {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "passing",
         "tags": [], "meta": {}, "checks": [], "outOfRange": false},
        {"id": "9d4e", "host": "10.1.2.4", "port": 8080, "health": "passing",
         "tags": [], "meta": {}, "checks": [], "outOfRange": false}\
        """);
    dashboard.answer("DELETE", "/api/workstation/services/web/instances/9d4e", 204, null);

    Run run = yard("remove", "web", "--host", "10.1.2.4", "--port", "8080");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Removed web at 10.1.2.4:8080.\n");
  }

  @Test
  void removeByPortOnMoreThanOneHostAsksForTheHost() {
    answerWebWithInstances(
        """
        {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "passing",
         "tags": [], "meta": {}, "checks": [], "outOfRange": false},
        {"id": "9d4e", "host": "10.1.2.4", "port": 8080, "health": "passing",
         "tags": [], "meta": {}, "checks": [], "outOfRange": false}\
        """);

    Run run = yard("remove", "web", "--port", "8080");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).contains("10.1.2.3").contains("10.1.2.4").contains("--host");
  }

  @Test
  void removeByPortWithNoInstanceThereSaysSo() {
    answerWebWithInstances(
        """
        {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "passing",
         "tags": [], "meta": {}, "checks": [], "outOfRange": false}\
        """);

    Run run = yard("remove", "web", "--port", "9090");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).isEqualTo("web has no Instance at port 9090.\n");
  }

  @Test
  void removeNeedsAnIdAPortOrAContainer() {
    Run run = yard("remove", "web");

    assertThat(run.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
    assertThat(run.err()).contains("--id").contains("--port").contains("--container");
  }
}
