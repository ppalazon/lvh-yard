package cc.lvhy.yard.dashboard.reachability;

import cc.lvhy.yard.dashboard.RuntimeNetwork;
import cc.lvhy.yard.dashboard.TraefikSettings;
import cc.lvhy.yard.dashboard.services.Alias;
import cc.lvhy.yard.dashboard.services.Aliases;
import cc.lvhy.yard.dashboard.services.Instance;
import cc.lvhy.yard.dashboard.services.Routing;
import cc.lvhy.yard.dashboard.services.Service;
import cc.lvhy.yard.dashboard.services.SourceKind;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.springframework.stereotype.Component;

/** Every check runs at once, so one that hangs costs its timeout only once. Nothing is stored. */
@Component
public class ReachabilityTester {

  /** How long a connection may take before the Instance, or Traefik, counts as not reached. */
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

  /** How long Traefik may take to answer once connected, while it waits on an Instance itself. */
  private static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(5);

  /** What Traefik answers, with a 404, for a hostname it has no Route for. */
  private static final String NO_ROUTE_BODY = "404 page not found";

  /**
   * What Traefik answers, with a 500, when it cannot set up a connection to an Instance other than
   * by a refusal or a timeout, such as a TLS error.
   */
  private static final String TRAEFIK_ERROR_BODY = "Internal Server Error";

  /** The most of an answer that is read, once its headers are longer. */
  private static final int ANSWER_BYTES = 4096;

  private final URI traefik;

  /** Null when Traefik has no HTTPS entry point. */
  private final URI traefikHttps;

  private final boolean traefikEnabled;

  private final Aliases aliases;

  private final RuntimeNetwork network;

  ReachabilityTester(TraefikSettings settings, Aliases aliases, RuntimeNetwork network) {
    this.traefik = settings.url();
    this.traefikHttps = settings.httpsUrl();
    this.traefikEnabled = settings.enabled();
    this.aliases = aliases;
    this.network = network;
  }

  public Reachability test(Service service) {
    List<RouteCheck> routeChecks = routeChecks(service);
    Routing routing = service.routing();
    try (ExecutorService checks = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<InstanceReachability>> instances =
          service.countedInstances().stream()
              .map(instance ->
                  checks.submit(
                      () -> connect(instance, routing != null && routing.routes(instance))))
              .toList();
      List<Future<RouteReachability>> routes =
          routeChecks.stream().map(check -> checks.submit(() -> request(check))).toList();
      List<InstanceReachability> testedInstances = results(instances);
      List<RouteReachability> testedRoutes = results(routes);
      return new Reachability(
          service.workstation(),
          service.name(),
          testedInstances,
          testedRoutes,
          routeChecks.isEmpty() ? noRoute(service) : null,
          routing,
          routing != null && routing.kind() == SourceKind.ALIAS ? fallback(service) : null,
          testedInstances.stream().noneMatch(InstanceReachability::failed)
              && testedRoutes.stream().allMatch(RouteReachability::reached)
              && !unsupportedOnly(service));
    }
  }

  /** The Alias of this Workstation's {@code service} that its Route uses now. */
  private Alias fallback(Service service) {
    String used = service.routing().source();
    return aliases.of(service.name()).stream()
        .filter(alias -> alias.id().equals(used))
        .findFirst()
        .orElse(null);
  }

  /**
   * HTTP and, when Traefik has an HTTPS entry point, HTTPS. A Route that requires a Client
   * Certificate has HTTPS only, so with HTTPS on it has no HTTP check.
   */
  private List<RouteCheck> routeChecks(Service service) {
    if (!traefikEnabled || !service.routed()) {
      return List.of();
    }
    boolean https = traefikHttps != null;
    boolean certificate = service.requiresClientCertificate();
    List<RouteCheck> checks = new ArrayList<>();
    if (!(https && certificate)) {
      checks.add(new RouteCheck(false, service.hostname(), certificate));
    }
    if (https) {
      checks.add(new RouteCheck(true, service.hostname(), certificate));
    }
    return checks;
  }

  private String noRoute(Service service) {
    if (!traefikEnabled) {
      return "No Traefik on this Workstation, so only its Instances are tested.";
    }
    if (!service.published()) {
      return "Not published, so only its Instances are tested.";
    }
    if (service.hostname() == null) {
      return "Local Scope on " + service.workstation() + ", so only its Instances are tested.";
    }
    if (unsupportedOnly(service)) {
      return RuntimeNetwork.IPV4_TARGETS_ONLY;
    }
    return "No Instance in range, so only its Instances are tested.";
  }

  private boolean unsupportedOnly(Service service) {
    return network.ipv4Only()
        && service.routing() == null
        && service.givenInstances().anyMatch(instance -> !network.supports(instance.host()));
  }

  /**
   * Closed at once. An Out-of-range Instance is skipped, because a connection here would reach
   * something else.
   */
  private InstanceReachability connect(Instance instance, boolean routed) {
    if (!network.supports(instance.host())) {
      return new InstanceReachability(
          instance.id(), instance.address(), false, false, false, RuntimeNetwork.IPV4_TARGETS_ONLY);
    }
    if (instance.outOfRange()) {
      return new InstanceReachability(instance.id(), instance.address(), false, true, false, null);
    }
    try (Socket socket = new Socket()) {
      socket.connect(
          new InetSocketAddress(instance.host(), instance.port()),
          (int) CONNECT_TIMEOUT.toMillis());
      return new InstanceReachability(instance.id(), instance.address(), true, false, routed, null);
    } catch (SocketTimeoutException e) {
      return new InstanceReachability(
          instance.id(),
          instance.address(),
          false,
          false,
          routed,
          "No answer within "
              + CONNECT_TIMEOUT.toSeconds()
              + " s: a firewall may drop the connection.");
    } catch (IOException e) {
      return new InstanceReachability(
          instance.id(),
          instance.address(),
          false,
          false,
          routed,
          network.ipv4Only() && e instanceof UnknownHostException
              ? "No IPv4 address was found for "
                  + instance.host()
                  + ". "
                  + RuntimeNetwork.IPV4_TARGETS_ONLY
              : describe(e));
    }
  }

  /**
   * It reached the Service when an Instance answered, whatever the status. Written by hand, because
   * the JDK's HTTP client does not let a request set its own Host header. Over HTTPS the hostname
   * is also the TLS server name, which Traefik picks the Route's certificate and TLS options by.
   */
  private RouteReachability request(RouteCheck check) {
    URI entry = check.https() ? traefikHttps : traefik;
    String hostname = check.hostname();
    String answer;
    try (Socket socket = new Socket()) {
      socket.connect(
          new InetSocketAddress(
              entry.getHost(), entry.getPort() < 0 ? (check.https() ? 443 : 80) : entry.getPort()),
          (int) CONNECT_TIMEOUT.toMillis());
      socket.setSoTimeout((int) ANSWER_TIMEOUT.toMillis());
      if (check.https()) {
        try (SSLSocket tls = handshake(socket, entry, hostname)) {
          answer = exchange(tls, hostname);
        }
      } else {
        answer = exchange(socket, hostname);
      }
    } catch (SocketTimeoutException e) {
      return failed(
          check,
          null,
          "Traefik at " + entry + " did not answer within " + ANSWER_TIMEOUT.toSeconds() + " s.");
    } catch (SSLException e) {
      if (check.clientCertificate() && asksForClientCertificate(e)) {
        return new RouteReachability(
            check.scheme(),
            hostname,
            true,
            null,
            "Traefik asks for a Client Certificate, as the Route requires. This test has none.",
            null);
      }
      return failed(
          check,
          null,
          "Traefik at "
              + entry
              + " did not complete a TLS handshake for "
              + hostname
              + ": "
              + describe(e)
              + ".");
    } catch (IOException e) {
      return failed(
          check,
          null,
          "Traefik does not answer at "
              + entry
              + ": "
              + describe(e)
              + ". Make sure that Traefik runs on this Workstation and listens at that address.");
    }
    int status = status(answer);
    if (status < 0) {
      return failed(check, null, entry + " is not Traefik: its answer is not HTTP.");
    }
    if (status == 404 && NO_ROUTE_BODY.equals(body(answer).strip())) {
      return failed(
          check,
          status,
          "Traefik has no Route for " + hostname + " yet, or no Instance is in range.");
    }
    if (status == 500 && TRAEFIK_ERROR_BODY.equals(body(answer).strip())) {
      return failed(
          check,
          status,
          "Traefik answered 500: it could not set up a connection to an Instance. If the Route uses"
              + " mutual TLS, check that the Service Certificate names the address of each"
              + " Instance. Run \"lvh-yard ws compose logs traefik\" to see why.");
    }
    if (status == 502 || status == 503 || status == 504) {
      return failed(check, status, "Traefik answered " + status + ": no Instance answered.");
    }
    return new RouteReachability(check.scheme(), hostname, true, status, null, null);
  }

  private static RouteReachability failed(RouteCheck check, Integer status, String error) {
    return new RouteReachability(check.scheme(), check.hostname(), false, status, null, error);
  }

  /**
   * Does not check Traefik's certificate. The test asks Traefik at the address of this
   * Workstation's own setting, and tests that the Route answers, not who signed the certificate.
   * The Workstation CA is not in this JVM's trust store.
   *
   * <p>Each handshake has its own {@link SSLContext}, so none resumes the session of another.
   * Traefik picks the TLS options of a Route by the server name in a full handshake only, and a
   * resumed session keeps the options of the hostname that started it.
   */
  private static SSLSocket handshake(Socket connected, URI entry, String hostname)
      throws IOException {
    SSLSocket tls =
        (SSLSocket)
            trustEveryCertificate()
                .getSocketFactory()
                .createSocket(connected, entry.getHost(), entry.getPort(), false);
    try {
      SSLParameters parameters = tls.getSSLParameters();
      parameters.setServerNames(List.of(new SNIHostName(hostname)));
      tls.setSSLParameters(parameters);
      tls.startHandshake();
      return tls;
    } catch (IOException | RuntimeException e) {
      tls.close();
      throw e;
    }
  }

  private static String exchange(Socket socket, String hostname) throws IOException {
    OutputStream out = socket.getOutputStream();
    out.write(
        ("GET / HTTP/1.1\r\nHost: "
                + hostname
                + "\r\nUser-Agent: lvh-yard\r\nConnection: close\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII));
    out.flush();
    return read(socket.getInputStream());
  }

  /**
   * Traefik rejects a client without a certificate with an alert. Only a Route with the TLS option
   * that requires one does, so the alert shows that the Route is there.
   */
  private static boolean asksForClientCertificate(SSLException error) {
    String message = error.getMessage();
    return message != null
        && (message.contains("certificate_required") || message.contains("bad_certificate"));
  }

  private static SSLContext trustEveryCertificate() {
    try {
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, new TrustManager[] {new AnyCertificate()}, null);
      return context;
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("The JVM has no TLS", e);
    }
  }

  private static final class AnyCertificate implements X509TrustManager {
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
      // Never called: this is the client side.
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {
      // Accepts the certificate, see handshake.
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }
  }

  /**
   * One request to Traefik for one hostname.
   *
   * @param clientCertificate whether the Route requires a Client Certificate, so it has no HTTP
   *     router
   */
  private record RouteCheck(boolean https, String hostname, boolean clientCertificate) {
    String scheme() {
      return https ? "https" : "http";
    }
  }

  /**
   * Reads the headers, and only enough of the body to tell Traefik's own 404 and 500 apart, so a
   * Service that streams its answer is not waited on.
   */
  private static String read(InputStream in) throws IOException {
    StringBuilder answer = new StringBuilder();
    byte[] chunk = new byte[ANSWER_BYTES];
    int count;
    while ((count = in.read(chunk)) >= 0) {
      answer.append(new String(chunk, 0, count, StandardCharsets.ISO_8859_1));
      int end = answer.indexOf("\r\n\r\n");
      int told =
          switch (status(answer.toString())) {
            case 404 -> NO_ROUTE_BODY.length();
            case 500 -> TRAEFIK_ERROR_BODY.length();
            default -> 0;
          };
      if (answer.length() >= ANSWER_BYTES || end >= 0 && answer.length() - end - 4 >= told) {
        break;
      }
    }
    return answer.toString();
  }

  /** -1 when there is no status line. */
  private static int status(String answer) {
    String[] statusLine = answer.lines().findFirst().orElse("").split(" ", 3);
    if (statusLine.length < 2 || !statusLine[0].startsWith("HTTP/")) {
      return -1;
    }
    try {
      return Integer.parseInt(statusLine[1]);
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private static String body(String answer) {
    int end = answer.indexOf("\r\n\r\n");
    return end < 0 ? "" : answer.substring(end + 4);
  }

  /** Its kind when it has no message. */
  private static String describe(Exception error) {
    return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
  }

  private static <T> List<T> results(List<Future<T>> checks) {
    return checks.stream()
        .map(check -> {
          try {
            return check.get();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while testing Reachability", e);
          } catch (ExecutionException e) {
            throw new IllegalStateException("A Reachability check failed", e.getCause());
          }
        })
        .toList();
  }
}
