package dev.palazon.lvhyard.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContainersTest extends YardTestSupport {

  @Test
  void containersGroupsComposeReplicasByServiceWithoutContainerNamesOrMappings() {
    replicaContainersAnswer();

    Run run = yard("containers");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            """
            Compose project lvh-yard-test
              http-https-echo: 2 containers; 8080, 8443
              whoami: 3 containers; 80
            """);
  }

  @Test
  void containersDetailsAddsActualContainerNamesMappingsAndHealthBelowEachService() {
    replicaContainersAnswer();

    Run run = yard("containers", "--details");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            """
            Compose project lvh-yard-test
              http-https-echo: 2 containers; 8080, 8443
                lvh-yard-test-http-https-echo-1: 0.0.0.0:37137->8080, 0.0.0.0:37138->8443; healthy
                lvh-yard-test-http-https-echo-2: 0.0.0.0:37139->8080, 0.0.0.0:37140->8443
              whoami: 3 containers; 80
                lvh-yard-test-whoami-1: 0.0.0.0:37141->80
                lvh-yard-test-whoami-2: 0.0.0.0:37142->80
                lvh-yard-test-whoami-3: 0.0.0.0:37143->80
            """);
  }

  @Test
  void containersGroupsInterleavedProjectsSeparatelyAndSortsDistinctContainerPorts() {
    dashboard.answer(
        "GET",
        "/api/workstation/containers",
        200,
        """
        [{"name": "beta-web-1", "composeProject": "beta", "composeService": "web",
          "service": "web", "running": true, "ports": [{"containerPort": 8080}]},
         {"name": "alpha-web-2", "composeProject": "alpha", "composeService": "web",
          "service": "web", "running": true, "ports": [{"containerPort": 9000}, {"containerPort": 80}]},
         {"name": "alpha-worker-1", "composeProject": "alpha", "composeService": "worker",
          "service": "worker", "running": true, "ports": []},
         {"name": "alpha-web-1", "composeProject": "alpha", "composeService": "web",
          "service": "web", "running": true, "ports": [{"containerPort": 443}, {"containerPort": 80}]}]
        """);

    Run run = yard("containers");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            """
            Compose project alpha
              web: 2 containers; 80, 443, 9000
              worker: 1 container; no ports
            Compose project beta
              web: 1 container; 8080
            """);
  }

  @Test
  void containersKeepsTheComposeServiceNameFirstWhenItsRegistrationNameDiffers() {
    dashboard.answer(
        "GET",
        "/api/workstation/containers",
        200,
        """
        [{"name": "shop-Front_End-1", "composeProject": "shop", "composeService": "Front_End",
          "service": "front-end", "running": true, "ports": [{"containerPort": 80}]}]
        """);

    Run run = yard("containers");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            """
            Compose project shop
              Front_End: 1 container; 80; registers as front-end
            """);
  }

  @Test
  void containersKeepsStandaloneContainersSeparateWithTheirProposedServiceNamesFirst() {
    standaloneContainersAnswer();

    Run run = yard("containers");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            """
            Other containers
              database: 1 container; 5432
              metrics: 1 container; 8080
              metrics: 1 container; 9090
              worker: 1 container; no ports
            """);
  }

  @Test
  void containersDetailsKeepsBindAddressesUnpublishedPortsAndContainersWithoutPorts() {
    standaloneContainersAnswer();

    Run run = yard("containers", "--details");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            """
            Other containers
              database: 1 container; 5432
                database-1: 5432 not published
              metrics: 1 container; 8080
                metrics-1: 0.0.0.0:37137->8080, 127.0.0.1:37138->8080, [::]:37137->8080
              metrics: 1 container; 9090
                metrics-2: 9090 not published; starting
              worker: 1 container; no ports
                worker-1: no ports
            """);
  }

  @Test
  void containersDetailsPreservesPublishedPortsWhenTheBindAddressIsUnknown() {
    dashboard.answer(
        "GET",
        "/api/workstation/containers",
        200,
        """
        [{"name": "web-1", "service": "web", "running": true,
          "ports": [{"containerPort": 8080, "hostPort": 37137}]}]
        """);

    Run run = yard("containers", "--details");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            """
            Other containers
              web: 1 container; 8080
                web-1: 37137->8080 (bind address unknown)
            """);
  }

  @Test
  void containersLeavesOutStoppedAndFollowedContainers() {
    dashboard.answer(
        "GET",
        "/api/workstation/containers",
        200,
        """
        [{"name": "api-1", "service": "api", "running": true, "ports": [{"containerPort": 80}]},
         {"name": "old-1", "service": "old", "running": false, "ports": []},
         {"name": "web-1", "service": "web", "running": true, "followedBy": "web",
          "ports": [{"containerPort": 80}]}]
        """);

    Run run = yard("containers");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            """
            Other containers
              api: 1 container; 80
            """);
  }

  @Test
  void containersSaysWhenEveryContainerIsFollowed() {
    dashboard.answer("GET", "/api/workstation/containers", 200, "[]");

    Run run = yard("containers");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Every running container is followed.\n");
  }
}
