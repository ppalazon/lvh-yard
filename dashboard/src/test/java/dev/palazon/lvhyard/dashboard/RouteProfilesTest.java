package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The test Workstation's {@code route-profiles.yaml}, as the API lists it and as its Consul KV
 * holds it for the other Workstations. Each test writes the whole file.
 */
class RouteProfilesTest extends DashboardTest {

  private static final String PATH = "/api/workstation/route-profiles";

  private static final JsonMapper JSON = new JsonMapper();

  @AfterEach
  void removeTheFile() throws IOException {
    Files.deleteIfExists(TestcontainersConfiguration.ROUTE_PROFILES);
    await().untilAsserted(() -> assertThat(listed().get("profiles")).isEmpty());
  }

  @Test
  void profileIsListedWithItsTypeAndCopiedToConsul() throws Exception {
    writeProfiles(
        """
        http:
          middlewares:
            behind-https:
              headers:
                customRequestHeaders:
                  X-Forwarded-Proto: https
        """);

    await()
        .untilAsserted(() ->
            assertThat(listed().toString())
                .isEqualTo("{\"profiles\":[{\"name\":\"behind-https\",\"type\":\"headers\"}]}"));
    assertThat(JSON.readTree(kv("behind-https")))
        .isEqualTo(
            JSON.readTree(
                """
                {"behind-https": {"headers": {"customRequestHeaders": {"X-Forwarded-Proto": "https"}}}}
                """));
  }

  @Test
  void profileRemovedFromTheFileIsDeletedFromConsul() throws Exception {
    writeProfiles(
        """
        http:
          middlewares:
            kept:
              headers:
                customResponseHeaders:
                  X-Kept: "yes"
            removed:
              compress: {}
        """);
    await().untilAsserted(() -> assertThat(kv("removed")).isNotNull());

    writeProfiles(
        """
        http:
          middlewares:
            kept:
              headers:
                customResponseHeaders:
                  X-Kept: "yes"
        """);

    await().untilAsserted(() -> assertThat(kv("removed")).isNull());
    assertThat(kv("kept")).isNotNull();
    assertThat(names()).containsExactly("kept");
  }

  @Test
  void chainIsCopiedWithItsMembers() throws Exception {
    writeProfiles(
        """
        http:
          middlewares:
            office-only:
              ipAllowList:
                sourceRange: [192.168.1.0/24]
            behind-https:
              headers:
                customRequestHeaders:
                  X-Forwarded-Proto: https
            protected:
              chain:
                middlewares: [office-only, behind-https]
        """);

    await().untilAsserted(() -> assertThat(kv("protected")).isNotNull());
    assertThat(JSON.readTree(kv("protected")))
        .isEqualTo(
            JSON.readTree(
                """
                {"protected": {"chain": {"middlewares": ["office-only", "behind-https"]}},
                 "office-only": {"ipAllowList": {"sourceRange": ["192.168.1.0/24"]}},
                 "behind-https": {"headers": {"customRequestHeaders": {"X-Forwarded-Proto": "https"}}}}
                """));
  }

  @Test
  void profilesThatCannotWorkOnAnotherWorkstationAreRefusedAndTheOthersApply() throws Exception {
    writeProfiles(
        """
        http:
          middlewares:
            fine:
              headers:
                customRequestHeaders:
                  X-Forwarded-Proto: https
            from-file:
              basicAuth:
                usersFile: /etc/traefik/users
            digest-from-file:
              digestAuth:
                usersFile: /etc/traefik/users
            auth-on-loopback:
              forwardAuth:
                address: http://127.0.0.1:4181/auth
            auth-on-localhost:
              forwardAuth:
                address: http://localhost:4181/auth
            auth-on-docker:
              forwardAuth:
                address: http://172.17.0.5:4181/auth
            auth-on-lan:
              forwardAuth:
                address: http://192.168.1.20:4181/auth
            with-plugin:
              plugin:
                my-plugin:
                  option: 1
            outside-chain:
              chain:
                middlewares: [fine, other@file]
            chain-of-refused:
              chain:
                middlewares: [fine, from-file]
            two-types:
              headers: {}
              compress: {}
        """);

    await().untilAsserted(() -> assertThat(kv("auth-on-lan")).isNotNull());
    JsonNode listed = listed();
    assertThat(listed.has("error")).isFalse();
    assertThat(error(listed, "fine")).isNull();
    assertThat(error(listed, "auth-on-lan")).isNull();
    assertThat(error(listed, "from-file")).contains("usersFile").contains("users");
    assertThat(error(listed, "digest-from-file")).contains("usersFile");
    assertThat(error(listed, "auth-on-loopback")).contains("127.0.0.1");
    assertThat(error(listed, "auth-on-localhost")).contains("localhost");
    assertThat(error(listed, "auth-on-docker")).contains("172.17.0.5");
    assertThat(error(listed, "with-plugin")).contains("plugin");
    assertThat(error(listed, "outside-chain")).contains("other@file");
    assertThat(error(listed, "chain-of-refused")).contains("from-file");
    assertThat(error(listed, "two-types")).contains("one middleware type");
    assertThat(names()).containsExactlyInAnyOrder("fine", "auth-on-lan");
    for (String refused :
        List.of(
            "from-file",
            "digest-from-file",
            "auth-on-loopback",
            "auth-on-localhost",
            "auth-on-docker",
            "with-plugin",
            "outside-chain",
            "chain-of-refused",
            "two-types")) {
      assertThat(kv(refused)).as(refused).isNull();
    }
  }

  @Test
  void fileThatIsNotYamlKeepsTheLastGoodProfiles() throws Exception {
    writeGoodProfile();

    writeProfiles("http: [unclosed\n");

    awaitKeptGoodProfileWithError("route-profiles.yaml");
  }

  @Test
  void unknownMiddlewareTypeKeepsTheLastGoodProfiles() throws Exception {
    writeGoodProfile();

    writeProfiles(
        """
        http:
          middlewares:
            good:
              headers:
                customRequestHeaders:
                  X-Changed: "yes"
            unknown-type:
              rewriteEverything: {}
        """);

    awaitKeptGoodProfileWithError("rewriteEverything");
    assertThat(kv("unknown-type")).isNull();
    assertThat(kv("good")).doesNotContain("X-Changed");
  }

  @Test
  void rateLimitIsOutOfScopeSoItIsAnUnknownType() throws Exception {
    writeGoodProfile();

    writeProfiles(
        """
        http:
          middlewares:
            good:
              headers:
                customRequestHeaders:
                  X-Forwarded-Proto: https
            limited:
              rateLimit:
                average: 100
        """);

    awaitKeptGoodProfileWithError("rateLimit");
    assertThat(kv("limited")).isNull();
  }

  private void writeGoodProfile() throws Exception {
    writeProfiles(
        """
        http:
          middlewares:
            good:
              headers:
                customRequestHeaders:
                  X-Forwarded-Proto: https
        """);
    await().untilAsserted(() -> assertThat(kv("good")).isNotNull());
  }

  /** The file's error names {@code expected}, and the profile {@code good} stays as it was. */
  private void awaitKeptGoodProfileWithError(String expected) {
    await().untilAsserted(() -> assertThat(listed().path("error").asString()).contains(expected));
    assertThat(listed().get("profiles").toString())
        .isEqualTo("[{\"name\":\"good\",\"type\":\"headers\"}]");
  }

  @Test
  void noFileMeansNoProfiles() throws Exception {
    Files.deleteIfExists(TestcontainersConfiguration.ROUTE_PROFILES);

    assertThat(listed().toString()).isEqualTo("{\"profiles\":[]}");
  }

  /** The names that reached Consul. */
  private List<String> names() throws Exception {
    ExecResult result =
        consulContainer.execInContainer("consul", "kv", "get", "-keys", "lvh-yard/route-profiles/");
    return result
        .getStdout()
        .lines()
        .map(key -> key.substring("lvh-yard/route-profiles/".length()))
        .toList();
  }

  private static String error(JsonNode listed, String profile) {
    for (JsonNode listedProfile : listed.get("profiles")) {
      if (listedProfile.get("name").asString().equals(profile)) {
        return listedProfile.has("error") ? listedProfile.get("error").asString() : null;
      }
    }
    throw new AssertionError(profile + " is not listed: " + listed);
  }

  private static void writeProfiles(String yaml) throws IOException {
    Files.writeString(TestcontainersConfiguration.ROUTE_PROFILES, yaml);
  }

  private JsonNode listed() {
    return get(PATH).getBody();
  }

  /** The profile's value in the test Workstation's Consul KV, or {@code null} when it has none. */
  private String kv(String profile) throws Exception {
    ExecResult result =
        consulContainer.execInContainer(
            "consul", "kv", "get", "lvh-yard/route-profiles/" + profile);
    return result.getExitCode() == 0 ? result.getStdout().strip() : null;
  }

  private static org.awaitility.core.ConditionFactory await() {
    return org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10));
  }
}
