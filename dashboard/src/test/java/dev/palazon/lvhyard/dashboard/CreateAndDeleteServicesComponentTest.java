package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.palazon.lvhyard.dashboard.consul.Consul.Registration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** Services that you create with a name and delete, through the REST API. */
class CreateAndDeleteServicesComponentTest extends DashboardComponentTest {

  @Test
  void createdServiceIsEmptyAndUnpublished() {
    String grafana = newServiceName("grafana");

    ResponseEntity<JsonNode> response = post(SERVICES_PATH, Map.of("name", grafana));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().get("name").asString()).isEqualTo(grafana);
    JsonNode service = get(servicePath(grafana)).getBody();
    assertThat(service.get("state").asString()).isEqualTo("unpublished");
    assertThat(service.get("instances")).isEmpty();
    assertThat(service.get("containerBindings")).isEmpty();
    assertThat(service.has("publication")).isFalse();
    assertThat(service.get("empty").asBoolean()).isTrue();
    assertThat(service.get("created").asBoolean()).isTrue();
  }

  @Test
  void createdServiceIsListedWithThisWorkstationsServices() {
    String grafana = newServiceName("grafana");

    create(grafana);

    assertThat(get(SERVICES_PATH).getBody().valueStream())
        .anyMatch(service -> service.get("name").asString().equals(grafana));
  }

  @Test
  void nameThatIsNotOneDnsLabelIsRefused() {
    ResponseEntity<JsonNode> response = post(SERVICES_PATH, Map.of("name", "My_Grafana"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("field").asString()).isEqualTo("name");
  }

  @Test
  void nameThatThisWorkstationUsesIsRefused() {
    String grafana = newServiceName("grafana");
    create(grafana);
    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));

    ResponseEntity<JsonNode> response = post(SERVICES_PATH, Map.of("name", grafana));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().get("detail").asString()).contains(grafana);
    assertThat(get(servicePath(grafana)).getBody().get("instances")).hasSize(1);
  }

  @Test
  void staticInstanceForAMissingServiceIsNotFound() {
    String grafana = newServiceName("grafana");

    ResponseEntity<JsonNode> response = post(instancesPath(grafana), Map.of("port", 3000));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().get("service").asString()).isEqualTo(grafana);
    assertThat(get(servicePath(grafana)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void containerBindingForAMissingServiceIsNotFound() {
    String web = newServiceName("web");

    ResponseEntity<JsonNode> response =
        post(containerBindingsPath(web), Map.of("container", newContainerName(), "port", 80));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get(servicePath(web)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void publishingAMissingServiceIsNotFound() {
    String web = newServiceName("web");

    assertThat(put(publicationPath(web)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void serviceWithASourceIsNotEmpty() {
    String grafana = newServiceName("grafana");

    register(grafana, Map.of("host", "10.1.2.3", "port", 3000));

    assertThat(get(servicePath(grafana)).getBody().get("empty").asBoolean()).isFalse();
  }

  @Test
  void deleteRemovesThePublicationAndEveryInstanceSource() {
    String web = newServiceName("web");
    String container = newContainerName();
    startContainer(container, 80, true);
    register(web, Map.of("host", "10.1.2.3", "port", 3000));
    bind(web, container, 80);
    publish(web);

    ResponseEntity<JsonNode> response = delete(servicePath(web));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(get(servicePath(web)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(consul.health(web)).isEmpty();
    assertThat(docker.runningContainers()).anyMatch(running -> running.name().equals(container));
    create(web);
    JsonNode again = get(servicePath(web)).getBody();
    assertThat(again.has("publication")).isFalse();
    assertThat(again.get("containerBindings")).isEmpty();
    assertThat(again.get("instances")).isEmpty();
  }

  @Test
  void deletingAMissingServiceIsNotFound() {
    assertThat(delete(servicePath(newServiceName("web"))).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void serviceThatOnlyForeignInstancesGiveShowsWithoutARecord() {
    String foreign = newServiceName("foreign");
    String id = registerForeignInstance(foreign);
    try {
      JsonNode service = get(servicePath(foreign)).getBody();

      assertThat(service.get("created").asBoolean()).isFalse();
      assertThat(service.get("empty").asBoolean()).isFalse();
    } finally {
      consul.deregister(id);
    }
  }

  @Test
  void deletedServiceWithForeignInstancesStays() {
    String web = newServiceName("web");
    register(web, Map.of("host", "10.1.2.3", "port", 3000));
    String id = registerForeignInstance(web);
    try {
      assertThat(delete(servicePath(web)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

      JsonNode service = get(servicePath(web)).getBody();
      assertThat(service.get("created").asBoolean()).isFalse();
      assertThat(service.get("instances")).hasSize(1);
      assertThat(service.get("instances").get(0).get("id").asString()).isEqualTo(id);
    } finally {
      consul.deregister(id);
    }
  }

  private String registerForeignInstance(String service) {
    String id = "foreign-" + UUID.randomUUID();
    consul.register(new Registration(id, service, "10.9.8.7", 8080, List.of(), Map.of(), null));
    return id;
  }
}
