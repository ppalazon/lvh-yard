package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;

/**
 * The Route checks of a Workstation whose Traefik also listens for HTTPS and calls Services over
 * mutual TLS, through a real Traefik with its own default certificate. It is a class of its own,
 * because HTTPS changes the Routes that every other test counts.
 */
class ReachabilityHttpsIT extends DashboardIT {

  private static final int HTTPS_PORT = TestcontainersConfiguration.freePorts(1)[0];

  private static final String PASSWORD = "changeit";

  private static GenericContainer<?> traefik;

  /** Traefik's {@code /etc/traefik/tls}: the files that the dashboard's transports name. */
  private static Path tls;

  /** The HTTP servers this test started, stopped after it. */
  private final List<HttpServer> servers = new ArrayList<>();

  /** A key pair, as PEM text for Traefik and as the keystore that a server uses. */
  private record Keys(Path keystore, String certificate, String key) {}

  @DynamicPropertySource
  static void https(DynamicPropertyRegistry registry) {
    registry.add("lvh-yard.traefik.https-port", () -> HTTPS_PORT);
    registry.add("lvh-yard.traefik.client-auth", () -> true);
    registry.add("lvh-yard.traefik.mtls", () -> true);
  }

  @BeforeEach
  void startTraefik() throws Exception {
    if (traefik == null) {
      Path dynamic = TestcontainersConfiguration.temporaryFolder("lvh-yard-traefik-dynamic");
      writeClientCertificateOption(dynamic);
      tls = TestcontainersConfiguration.temporaryFolder("lvh-yard-traefik-tls");
      Keys own = generate(null);
      Files.writeString(tls.resolve("mtls.crt"), own.certificate());
      Files.writeString(tls.resolve("mtls.key"), own.key());
      Files.writeString(tls.resolve("mtls-ca.crt"), own.certificate());
      traefik =
          TestcontainersConfiguration.traefikWith(
                  port,
                  "--entryPoints.websecure.address=127.0.0.1:" + HTTPS_PORT,
                  "--providers.file.directory=/dynamic")
              .withFileSystemBind(dynamic.toString(), "/dynamic", BindMode.READ_ONLY)
              .withFileSystemBind(tls.toString(), "/etc/traefik/tls", BindMode.READ_ONLY)
              .waitingFor(
                  TestcontainersConfiguration.traefikReady(
                      TestcontainersConfiguration.TRAEFIK_ADMIN_PORT,
                      TestcontainersConfiguration.TRAEFIK_PORT,
                      HTTPS_PORT));
      traefik.start();
    }
  }

  @AfterEach
  void stopServers() throws Exception {
    servers.forEach(server -> server.stop(0));
    removeAllRouteProfiles();
  }

  @AfterAll
  static void stopTraefik() {
    if (traefik != null) {
      traefik.stop();
      traefik = null;
    }
  }

  @Test
  void publishedServiceIsRequestedOverHttpAndOverHttps() {
    String web = newServiceName("web");
    register(web, Map.of("host", "127.0.0.1", "port", answerHttpOnLoopback()));
    publish(web);

    JsonNode result =
        await()
            .atMost(Duration.ofSeconds(15))
            .until(
                () -> get(reachabilityPath(WORKSTATION, web)).getBody(),
                tested -> tested.get("passed").asBoolean());

    assertThat(result.get("routes").valueStream().map(route -> route.get("scheme").asString()))
        .containsExactly("http", "https");
    assertThat(result.get("routes").valueStream().map(route -> route.get("hostname").asString()))
        .containsOnly(web + ".lvh.test");
    assertThat(result.get("routes").valueStream().map(route -> route.get("status").asInt()))
        .containsOnly(200);
  }

  @Test
  void routeThatRequiresAClientCertificateIsReachedOverHttpsOnly() {
    String web = newServiceName("web");
    putRouteProfiles(Map.of("certificate", Map.of("clientCertificate", Map.of())));
    register(web, Map.of("host", "127.0.0.1", "port", answerHttpOnLoopback()));
    publish(web);
    put(publicationPath(web), Map.of("profiles", List.of("certificate")));

    // Traefik loads the new Route within a second. Before that, the check finds no Route.
    JsonNode result =
        await()
            .atMost(Duration.ofSeconds(15))
            .until(
                () -> get(reachabilityPath(WORKSTATION, web)).getBody(),
                tested -> tested.get("routes").valueStream().anyMatch(route -> route.has("note")));

    assertThat(result.get("routes")).hasSize(1);
    JsonNode route = result.get("routes").get(0);
    assertThat(route.get("scheme").asString()).isEqualTo("https");
    assertThat(route.get("reached").asBoolean()).isTrue();
    assertThat(route.has("status")).isFalse();
    assertThat(route.get("note").asString()).contains("Client Certificate");
    assertThat(result.get("passed").asBoolean()).isTrue();
  }

  /**
   * The certificate of the Instance names one Service and not the address 127.0.0.1, which the
   * Route uses. The Route of that Service reaches it. The Route of another Service, to the same
   * Instance and the same CA, is refused.
   */
  @Test
  void mutualTlsChecksTheNameOfTheServiceAndNotTheAddressOfTheInstance() throws Exception {
    String right = newServiceName("web");
    String wrong = newServiceName("other");
    Keys backend = generate(right);
    Files.writeString(tls.resolve("mtls-ca.crt"), backend.certificate());
    int listening = answerHttpsOnLoopback(backend);
    for (String name : List.of(right, wrong)) {
      register(name, Map.of("host", "127.0.0.1", "port", listening));
      publish(name);
      put(publicationPath(name), Map.of("mutualTls", true));
    }

    // Traefik loads the Route of each entry point at its own moment.
    JsonNode reached =
        await()
            .atMost(Duration.ofSeconds(20))
            .until(
                () -> get(reachabilityPath(WORKSTATION, right)).getBody(),
                tested -> allRoutesAnswered(tested, 200));
    JsonNode refused =
        await()
            .atMost(Duration.ofSeconds(20))
            .until(
                () -> get(reachabilityPath(WORKSTATION, wrong)).getBody(),
                tested -> allRoutesAnswered(tested, 500));

    assertThat(reached.get("passed").asBoolean()).isTrue();
    assertThat(refused.get("passed").asBoolean()).isFalse();
    assertThat(refused.get("routes").get(0).get("error").asString()).contains("mutual TLS");
  }

  @Test
  void routeWhoseTlsToTheInstanceFailsIsNotReached() {
    String web = newServiceName("web");
    // Traefik starts TLS with an Instance that speaks plain HTTP, and answers 500 for it.
    register(web, Map.of("host", "127.0.0.1", "port", answerHttpOnLoopback()));
    publish(web);
    put(publicationPath(web), Map.of("mutualTls", true));

    // Traefik loads the Route of each entry point at its own moment.
    JsonNode result =
        await()
            .atMost(Duration.ofSeconds(15))
            .until(
                () -> get(reachabilityPath(WORKSTATION, web)).getBody(),
                tested -> allRoutesAnswered(tested, 500));

    assertThat(result.get("routes").valueStream().map(route -> route.get("scheme").asString()))
        .containsExactly("http", "https");
    assertThat(result.get("routes").valueStream().map(route -> route.get("reached").asBoolean()))
        .containsOnly(false);
    assertThat(result.get("routes").get(0).get("error").asString()).contains("mutual TLS");
    assertThat(result.get("passed").asBoolean()).isFalse();
  }

  private static boolean allRoutesAnswered(JsonNode tested, int status) {
    return tested.get("routes").size() == 2
        && tested
            .get("routes")
            .valueStream()
            .allMatch(route -> route.path("status").asInt() == status);
  }

  /**
   * The option {@code yard-client-certificate@file}, as the CLI writes it, with a CA that signs
   * nothing: the test sends no certificate.
   */
  private static void writeClientCertificateOption(Path dynamic) throws Exception {
    Files.writeString(dynamic.resolve("client-ca.crt"), generate(null).certificate());
    Files.writeString(
        dynamic.resolve("client-auth.yaml"),
        """
        tls:
          options:
            yard-client-certificate:
              minVersion: VersionTLS12
              clientAuth:
                caFiles:
                  - /dynamic/client-ca.crt
                clientAuthType: RequireAndVerifyClientCert
        """);
  }

  /**
   * A self-signed key pair. With a {@code name}, its certificate names that DNS name and nothing
   * else, as {@code ws tls ca issue service} names the Service. A self-signed certificate is its
   * own CA for Traefik.
   */
  private static Keys generate(String name) throws Exception {
    Path keystore =
        TestcontainersConfiguration.temporaryFolder("lvh-yard-keys").resolve("keys.p12");
    List<String> command =
        new ArrayList<>(
            List.of(
                Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair",
                "-alias",
                "key",
                "-keyalg",
                "EC",
                "-dname",
                "CN=test",
                "-validity",
                "1",
                "-storetype",
                "PKCS12",
                "-keystore",
                keystore.toString(),
                "-storepass",
                PASSWORD));
    if (name != null) {
      command.addAll(List.of("-ext", "san=dns:" + name));
    }
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    if (process.waitFor() != 0) {
      throw new AssertionError("keytool failed: " + output);
    }
    KeyStore store = load(keystore);
    return new Keys(
        keystore,
        pem("CERTIFICATE", store.getCertificate("key").getEncoded()),
        pem("PRIVATE KEY", store.getKey("key", PASSWORD.toCharArray()).getEncoded()));
  }

  private static KeyStore load(Path keystore) throws IOException, GeneralSecurityException {
    KeyStore store = KeyStore.getInstance("PKCS12");
    try (InputStream in = Files.newInputStream(keystore)) {
      store.load(in, PASSWORD.toCharArray());
    }
    return store;
  }

  private static String pem(String type, byte[] der) {
    return "-----BEGIN "
        + type
        + "-----\n"
        + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
        + "\n-----END "
        + type
        + "-----\n";
  }

  /** An HTTP server on a free loopback port that answers every request with 200. */
  private int answerHttpOnLoopback() {
    try {
      return start(HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** An HTTPS server on a free loopback port, with the certificate of {@code keys}. */
  private int answerHttpsOnLoopback(Keys keys) throws Exception {
    KeyManagerFactory factory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    factory.init(load(keys.keystore()), PASSWORD.toCharArray());
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(factory.getKeyManagers(), null, null);
    HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setHttpsConfigurator(new HttpsConfigurator(context));
    return start(server);
  }

  private int start(HttpServer server) {
    server.createContext(
        "/",
        exchange -> {
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    server.start();
    servers.add(server);
    return server.getAddress().getPort();
  }

  private static String reachabilityPath(String workstation, String service) {
    return ReachabilityIT.reachabilityPath(workstation, service);
  }
}
