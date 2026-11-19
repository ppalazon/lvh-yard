package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import cc.lvhy.yard.dashboard.consul.Consul.Registration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** An import in add mode: it adds what is new and skips what exists. */
class BackupImportComponentTest extends BackupComponentTest {

  @Test
  void newServiceGetsItsStaticInstanceAndStaysUnpublished() {
    String grafana = newServiceName("grafana");

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 2
            services:
              %s:
                sources:
                  - static-instance:
                      host: 10.1.2.3
                      port: 3000
            """
                .formatted(grafana));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(entries(response))
        .containsExactly(
            "add service " + grafana, "add static-instance 10.1.2.3:3000 of " + grafana);
    JsonNode service = get(servicePath(grafana)).getBody();
    assertThat(service.get("instances").get(0).get("host").asString()).isEqualTo("10.1.2.3");
    assertThat(service.get("instances").get(0).get("port").asInt()).isEqualTo(3000);
    assertThat(service.get("state").asString()).isEqualTo("unpublished");
  }

  @Test
  void existingEntriesAreSkippedWithAReason() {
    String grafana = newServiceName("grafana");
    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));
    register(grafana, Map.of("host", "10.1.2.3", "port", 3001, "tags", List.of("blue")));

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 2
            services:
              %s:
                sources:
                  - static-instance:
                      host: 10.1.2.3
                      port: 3000
                  - static-instance:
                      host: 10.1.2.3
                      port: 3001
                      tags: [green]
                  - static-instance:
                      host: 10.1.2.3
                      port: 3002
            """
                .formatted(grafana));

    assertThat(entries(response))
        .containsExactly(
            "skip service " + grafana + ": It exists already.",
            "skip static-instance 10.1.2.3:3000 of " + grafana + ": It is the same already.",
            "skip static-instance 10.1.2.3:3001 of " + grafana + ": It exists already.",
            "add static-instance 10.1.2.3:3002 of " + grafana,
            "skip instance-priority " + grafana + ": It exists already.");
    JsonNode instances = get(servicePath(grafana)).getBody().get("instances");
    assertThat(instances).hasSize(3);
    assertThat(
            instances
                .valueStream()
                .filter(instance -> instance.get("port").asInt() == 3001)
                .findFirst()
                .orElseThrow()
                .get("tags")
                .get(0)
                .asString())
        .isEqualTo("blue");
  }

  @Test
  void staticInstanceWithoutAHostIsAtTheLanAddressAndMatchesThere() {
    String grafana = newServiceName("grafana");
    String loki = newServiceName("loki");
    register(grafana, Map.of("port", 3000));

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 2
            services:
              %s:
                sources:
                  - static-instance:
                      port: 3000
              %s:
                sources:
                  - static-instance:
                      host: %s
                      port: 3000
            """
                .formatted(grafana, loki, lanAddress()));

    String address = lanAddress() + ":3000";
    assertThat(entries(response))
        .containsExactly(
            "skip service " + grafana + ": It exists already.",
            "skip static-instance " + address + " of " + grafana + ": It is the same already.",
            "add service " + loki,
            "add static-instance " + address + " of " + loki);
    assertThat(get(servicePath(grafana)).getBody().get("instances")).hasSize(1);
    assertThat(get(servicePath(loki)).getBody().get("instances").get(0).get("host").asString())
        .isEqualTo(lanAddress());
  }

  @Test
  void containerBindingMatchesByItsId() {
    String web = newServiceName("web");
    bindCompose(web, "shop", web, 8080);

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 2
            services:
              %s:
                sources:
                  - container-binding:
                      compose: {project: shop, service: %s}
                      port: 8080
                  - container-binding:
                      container: %s-1
                      port: 80
            """
                .formatted(web, web, web));

    assertThat(entries(response))
        .containsExactly(
            "skip service " + web + ": It exists already.",
            "skip container-binding compose:shop:"
                + web
                + ":8080 of "
                + web
                + ": It is the same already.",
            "add container-binding " + web + "-1:80 of " + web,
            "skip instance-priority " + web + ": It exists already.");
    assertThat(
            get(servicePath(web))
                .getBody()
                .get("containerBindings")
                .valueStream()
                .map(binding -> binding.get("id").asString()))
        .containsExactlyInAnyOrder("compose:shop:" + web + ":8080", web + "-1:80");
  }

  @Test
  void publicationCanNameARouteProfileOfTheSameFile() {
    String grafana = newServiceName("grafana");
    String compress = newServiceName("compress");

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 2
            route-profiles:
              %s:
                compress: {}
            services:
              %s:
                publication:
                  scope: yard
                  route-profiles: [%s]
                sources:
                  - static-instance:
                      host: 10.1.2.3
                      port: 3000
            """
                .formatted(compress, grafana, compress));

    assertThat(entries(response))
        .containsExactly(
            "add route-profile " + compress,
            "add service " + grafana,
            "add static-instance 10.1.2.3:3000 of " + grafana,
            "add publication " + grafana);
    JsonNode service = get(servicePath(grafana)).getBody();
    assertThat(service.get("publication").get("scope").asString()).isEqualTo("yard");
    assertThat(service.get("profiles").valueStream().map(JsonNode::asString))
        .containsExactly(compress);
  }

  @Test
  void serviceWithOnlyAPublicationIsAdded() {
    String grafana = newServiceName("grafana");

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 2
            services:
              %s:
                publication:
                  scope: local
            """
                .formatted(grafana));

    assertThat(entries(response))
        .containsExactly("add service " + grafana, "add publication " + grafana);
    assertThat(get(servicePath(grafana)).getBody().get("publication").get("scope").asString())
        .isEqualTo("local");
  }

  @Test
  void mutualTlsOfAPublicationIsRestored() {
    String grafana = newServiceName("grafana");

    importBackup(
        """
        version: 3
        services:
          %s:
            publication:
              scope: yard
              mutual-tls: true
        """
            .formatted(grafana));

    JsonNode publication = get(servicePath(grafana)).getBody().get("publication");
    assertThat(publication.get("scope").asString()).isEqualTo("yard");
    assertThat(publication.get("mutualTls").asBoolean()).isTrue();
  }

  @Test
  void serviceThatOnlyForeignInstancesGiveGetsItsRecordAndSources() {
    String grafana = newServiceName("grafana");
    String id = "foreign-" + UUID.randomUUID();
    consul.register(new Registration(id, grafana, "10.9.8.7", 8080, List.of(), Map.of(), null));
    try {
      ResponseEntity<JsonNode> response =
          importBackup(
              """
              version: 2
              services:
                %s:
                  sources:
                    - static-instance:
                        host: 10.1.2.3
                        port: 3000
                  instance-priority: false
              """
                  .formatted(grafana));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      JsonNode service = get(servicePath(grafana)).getBody();
      assertThat(service.get("created").asBoolean()).isTrue();
      assertThat(service.get("instances")).hasSize(2);
      assertThat(service.get("instancePriority").asBoolean()).isFalse();
    } finally {
      consul.deregister(id);
    }
  }

  @Test
  void emptyServiceIsAdded() {
    String notes = newServiceName("notes");

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 2
            services:
              %s:
                publication: none
                instance-priority: true
            """
                .formatted(notes));

    assertThat(entries(response))
        .containsExactly(
            "add service " + notes, "skip publication " + notes + ": It is Unpublished already.");
    JsonNode service = get(servicePath(notes)).getBody();
    assertThat(service.get("created").asBoolean()).isTrue();
    assertThat(service.get("empty").asBoolean()).isTrue();
  }

  @Test
  void orderAndToggleAreRestored() {
    String web = newServiceName("web");

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 2
            services:
              %s:
                instance-priority: false
                sources:
                  - alias:
                      workstation: %s
                      service: nothing-here
                  - static-instance:
                      host: 10.1.2.4
                      port: 3000
                  - container-binding:
                      compose: {project: shop, service: %s}
                      port: 8080
                  - static-instance:
                      host: 10.1.2.3
                      port: 3000
            """
                .formatted(web, RemoteWorkstation.NAME, web));

    assertThat(entries(response)).contains("add instance-priority " + web);
    JsonNode service = get(servicePath(web)).getBody();
    assertThat(service.get("instancePriority").asBoolean()).isFalse();
    assertThat(service.get("sources").valueStream().map(source -> source.get("kind").asString()))
        .containsExactly("alias", "static", "bound", "static");
    assertThat(service.get("sources").get(0).get("id").asString())
        .isEqualTo(RemoteWorkstation.NAME + "/nothing-here");
    assertThat(service.get("sources").get(2).get("id").asString())
        .isEqualTo("compose:shop:" + web + ":8080");
    String first = service.get("sources").get(1).get("id").asString();
    assertThat(
            service
                .get("instances")
                .valueStream()
                .filter(instance -> instance.get("id").asString().equals(first))
                .findFirst()
                .orElseThrow()
                .get("host")
                .asString())
        .isEqualTo("10.1.2.4");
  }

  @Test
  void existingServiceKeepsItsOrderAndToggle() {
    String web = newServiceName("web");
    String first = register(web, Map.of("host", "10.1.2.3", "port", 3000)).get("id").asString();
    String second = register(web, Map.of("host", "10.1.2.4", "port", 3000)).get("id").asString();

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 2
            services:
              %s:
                instance-priority: false
                sources:
                  - static-instance:
                      host: 10.1.2.4
                      port: 3000
                  - static-instance:
                      host: 10.1.2.3
                      port: 3000
            """
                .formatted(web));

    assertThat(entries(response))
        .containsExactly(
            "skip service " + web + ": It exists already.",
            "skip static-instance 10.1.2.4:3000 of " + web + ": It is the same already.",
            "skip static-instance 10.1.2.3:3000 of " + web + ": It is the same already.",
            "skip instance-priority " + web + ": It exists already.");
    JsonNode service = get(servicePath(web)).getBody();
    assertThat(service.get("instancePriority").asBoolean()).isTrue();
    assertThat(service.get("sources").valueStream().map(source -> source.get("id").asString()))
        .containsExactly(first, second);
  }

  @Test
  void bindingATargetThatAnotherServiceFollowsIsAdded() {
    String web = newServiceName("web");
    String shop = newServiceName("shop");
    bindCompose(web, "shop", web, 8080);

    ResponseEntity<JsonNode> response =
        importBackup(
            """
            version: 2
            services:
              %s:
                sources:
                  - container-binding:
                      compose: {project: shop, service: %s}
                      port: 9090
            """
                .formatted(shop, web));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(entries(response))
        .containsExactly(
            "add service " + shop,
            "add container-binding compose:shop:" + web + ":9090 of " + shop);
    assertThat(get(servicePath(shop)).getBody().get("containerBindings")).hasSize(1);
  }
}
