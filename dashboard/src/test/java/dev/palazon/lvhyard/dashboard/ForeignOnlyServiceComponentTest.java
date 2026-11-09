package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.palazon.lvhyard.dashboard.consul.Consul.Registration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * A Service that only Foreign Instances give works like a Service that you created, with no create
 * step.
 */
class ForeignOnlyServiceComponentTest extends DashboardComponentTest {

  private final List<String> foreignIds = new ArrayList<>();

  @AfterEach
  void deregisterForeignInstances() {
    foreignIds.forEach(consul::deregister);
  }

  /** A Service whose only Instance another tool registered straight in Consul. */
  private String foreignOnly(String prefix) {
    String service = newServiceName(prefix);
    String id = "foreign-" + UUID.randomUUID();
    consul.register(new Registration(id, service, "10.9.8.7", 8080, List.of(), Map.of(), null));
    foreignIds.add(id);
    return service;
  }

  /** A Service of the remote Workstation with Yard Scope. */
  private String sharedRemoteService(String prefix) {
    String service = newServiceName(prefix);
    remote.register(service, Map.of("host", "10.9.8.7", "port", 3000));
    remote.publish(service, "yard");
    return service;
  }

  @Test
  void itsNameIsRefusedAsANewService() {
    String grafana = foreignOnly("grafana");

    ResponseEntity<JsonNode> response = post(SERVICES_PATH, Map.of("name", grafana));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().get("detail").asString()).contains(grafana);
  }

  @Test
  void itPublishesWithRouteProfilesAndKeepsItsRecordWhenItsInstancesGo() {
    putRouteProfiles(RouteProfilesComponentTest.json("{\"foreign-compress\": {\"compress\": {}}}"));
    String grafana = foreignOnly("grafana");

    ResponseEntity<JsonNode> response =
        put(
            publicationPath(grafana),
            Map.of("scope", "yard", "profiles", List.of("foreign-compress")));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    foreignIds.forEach(consul::deregister);
    JsonNode service = get(servicePath(grafana)).getBody();
    assertThat(service.get("publication").get("scope").asString()).isEqualTo("yard");
    assertThat(service.get("profiles").valueStream().map(JsonNode::asString))
        .containsExactly("foreign-compress");
    assertThat(service.get("created").asBoolean()).isTrue();
  }

  @Test
  void aStaticInstanceAndAContainerBindingAttachToIt() {
    String web = foreignOnly("web");
    String container = newContainerName();
    startContainer(container, 80, true);

    ResponseEntity<JsonNode> instance =
        post(instancesPath(web), Map.of("host", "10.1.2.3", "port", 3000));
    ResponseEntity<JsonNode> binding =
        post(containerBindingsPath(web), Map.of("container", container, "port", 80));

    assertThat(instance.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(binding.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode service = get(servicePath(web)).getBody();
    assertThat(service.get("created").asBoolean()).isTrue();
    assertThat(InstancePriorityComponentTest.ids(service.get("sources")))
        .containsExactly(
            foreignIds.getFirst(),
            instance.getBody().get("id").asString(),
            binding.getBody().get("id").asString());
  }

  @Test
  void itsInstancePrioritySetsWithNoCreateStep() {
    String web = foreignOnly("web");

    ResponseEntity<JsonNode> response = put(instancePriorityPath(web), Map.of("on", false));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(get(servicePath(web)).getBody().get("instancePriority").asBoolean()).isFalse();
  }

  @Test
  void anAliasAttachesToItAndCreateDoesNotPublishIt() {
    String grafana = sharedRemoteService("grafana");
    String loki = sharedRemoteService("loki");
    String web = foreignOnly("web");

    ResponseEntity<JsonNode> added =
        post(
            AliasesComponentTest.aliasesPath(web),
            Map.of("workstation", RemoteWorkstation.NAME, "service", grafana));
    ResponseEntity<JsonNode> withCreate =
        post(
            AliasesComponentTest.aliasesPath(web),
            Map.of("workstation", RemoteWorkstation.NAME, "service", loki, "create", true));

    assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(withCreate.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    JsonNode service = get(servicePath(web)).getBody();
    assertThat(InstancePriorityComponentTest.ids(service.get("sources")))
        .containsExactly(
            foreignIds.getFirst(),
            RemoteWorkstation.NAME + "/" + grafana,
            RemoteWorkstation.NAME + "/" + loki);
    assertThat(service.has("publication")).isFalse();
  }

  @Test
  void itHasNoAliasesBeforeItsRecordIsWritten() {
    String web = foreignOnly("web");

    ResponseEntity<JsonNode> response = get(AliasesComponentTest.aliasesPath(web));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().size()).isZero();
  }

  @Test
  void aMissingServiceIsStillNotFound() {
    String web = newServiceName("web");

    assertThat(put(instancePriorityPath(web), Map.of("on", false)).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(get(servicePath(web)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
