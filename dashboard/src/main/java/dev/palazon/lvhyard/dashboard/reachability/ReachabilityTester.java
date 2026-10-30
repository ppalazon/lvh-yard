package dev.palazon.lvhyard.dashboard.reachability;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import dev.palazon.lvhyard.dashboard.services.Alias;
import dev.palazon.lvhyard.dashboard.services.Aliases;
import dev.palazon.lvhyard.dashboard.services.Instance;
import dev.palazon.lvhyard.dashboard.services.Service;

/**
 * Tests a Service's Reachability from this Workstation, on request: a TCP connection straight to each Instance, and a
 * request through this Workstation's Traefik for each hostname it routes to the Service: the Service's own and its
 * active Aliases'. Every check runs at once, so one that hangs costs its timeout only once. Nothing is stored.
 */
@Component
public class ReachabilityTester {

    /** How long a connection may take before the Instance, or Traefik, counts as not reached. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    /** How long Traefik may take to answer once connected, while it waits on an Instance itself. */
    private static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(5);

    /** What Traefik answers, with a 404, for a hostname it has no Route for. */
    private static final String NO_ROUTE_BODY = "404 page not found";

    /** The most of an answer that is read, once its headers are longer. */
    private static final int ANSWER_BYTES = 4096;

    private final URI traefik;

    private final Aliases aliases;

    ReachabilityTester(TraefikSettings settings, Aliases aliases) {
        this.traefik = settings.url();
        this.aliases = aliases;
    }

    /** Tests {@code service} now. */
    public Reachability test(Service service) {
        List<String> hostnames = hostnames(service);
        try (ExecutorService checks = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<InstanceReachability>> instances = service.instances().stream()
                    .map(instance -> checks.submit(() -> connect(service, instance))).toList();
            List<Future<RouteReachability>> routes = hostnames.stream()
                    .map(hostname -> checks.submit(() -> request(hostname))).toList();
            return new Reachability(service.workstation(), service.name(), results(instances), results(routes),
                    hostnames.isEmpty() ? noRoute(service) : null);
        }
    }

    /**
     * Every hostname this Workstation routes to {@code service}: its own, then its active Aliases', none when it has
     * no Route here. Only a Service with Yard Scope can have an active Alias, so the Aliases are read only for one.
     */
    private List<String> hostnames(Service service) {
        if (!service.routed()) {
            return List.of();
        }
        Stream<String> aliased = !service.sharedWithYard() ? Stream.empty()
                : aliases.all().stream()
                        .filter(alias -> alias.active() && alias.workstation().equals(service.workstation())
                                && alias.service().equals(service.name()))
                        .map(Alias::hostname);
        return Stream.concat(Stream.of(service.hostname()), aliased).toList();
    }

    /** Why this Workstation has no Route to {@code service}. */
    private static String noRoute(Service service) {
        if (!service.published()) {
            return "Not published: it has no Route, so only its Instances are tested.";
        }
        if (service.hostname() == null) {
            return "Published with Local Scope: only " + service.workstation() + " routes it, so only its Instances"
                    + " are tested from here.";
        }
        return "It has no Instance this Workstation can route to: none is registered, or each is out of range. So"
                + " only its Instances are tested.";
    }

    /**
     * A TCP connection straight to {@code instance}, closed at once. An Out-of-range Instance is not connected to:
     * its address is on its own Workstation, so a connection here would reach something else.
     */
    private static InstanceReachability connect(Service service, Instance instance) {
        if (instance.outOfRange()) {
            return new InstanceReachability(instance.id(), instance.address(), false, "Out of range: only "
                    + service.workstation() + " can connect to this address, so it is not tested from here.");
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(instance.host(), instance.port()),
                    (int) CONNECT_TIMEOUT.toMillis());
            return new InstanceReachability(instance.id(), instance.address(), true, null);
        } catch (SocketTimeoutException e) {
            return new InstanceReachability(instance.id(), instance.address(), false,
                    "No answer within " + CONNECT_TIMEOUT.toSeconds() + " s: a firewall may drop the connection.");
        } catch (IOException e) {
            return new InstanceReachability(instance.id(), instance.address(), false, describe(e));
        }
    }

    /**
     * A {@code GET /} for {@code hostname} through Traefik. It reached the Service when Traefik passed it on and an
     * Instance answered, whatever the status: not when Traefik has no Route for the hostname, or could not get an
     * answer from an Instance. Written by hand, as the JDK's HTTP client does not let a request name its own host.
     */
    private RouteReachability request(String hostname) {
        String answer;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(traefik.getHost(), traefik.getPort() < 0 ? 80 : traefik.getPort()),
                    (int) CONNECT_TIMEOUT.toMillis());
            socket.setSoTimeout((int) ANSWER_TIMEOUT.toMillis());
            OutputStream out = socket.getOutputStream();
            out.write(("GET / HTTP/1.1\r\nHost: " + hostname + "\r\nUser-Agent: lvh-yard\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
            answer = read(socket.getInputStream());
        } catch (SocketTimeoutException e) {
            return new RouteReachability(hostname, false, null, "Traefik at " + traefik + " did not answer within "
                    + ANSWER_TIMEOUT.toSeconds() + " s.");
        } catch (IOException e) {
            return new RouteReachability(hostname, false, null, "Traefik does not answer at " + traefik + ": "
                    + describe(e) + ". Start it with: mise run up");
        }
        int status = status(answer);
        if (status < 0) {
            return new RouteReachability(hostname, false, null, "What answers at " + traefik + " is not Traefik:"
                    + " its answer is not HTTP.");
        }
        if (status == 404 && body(answer).strip().equals(NO_ROUTE_BODY)) {
            return new RouteReachability(hostname, false, status, "Traefik has no Route for " + hostname + ": it"
                    + " has not read its Routes since the Service was published, or no Instance is in range.");
        }
        if (status == 502 || status == 503 || status == 504) {
            return new RouteReachability(hostname, false, status, "Traefik answered " + status + ": it could not"
                    + " get an answer from an Instance.");
        }
        return new RouteReachability(hostname, true, status, null);
    }

    /**
     * The start of the answer on {@code in}: its status line and headers, and as much of its body as has come with
     * them or tells Traefik's own 404 apart. It stops there, so a Service that streams its answer is not waited on.
     */
    private static String read(InputStream in) throws IOException {
        StringBuilder answer = new StringBuilder();
        byte[] chunk = new byte[ANSWER_BYTES];
        int count;
        while ((count = in.read(chunk)) >= 0) {
            answer.append(new String(chunk, 0, count, StandardCharsets.ISO_8859_1));
            int end = answer.indexOf("\r\n\r\n");
            boolean noRouteTold = status(answer.toString()) != 404
                    || answer.length() - end - 4 >= NO_ROUTE_BODY.length();
            if (answer.length() >= ANSWER_BYTES || end >= 0 && noRouteTold) {
                break;
            }
        }
        return answer.toString();
    }

    /** The status code on {@code answer}'s status line, such as 200, or -1 when it has none. */
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

    /** What follows {@code answer}'s headers. */
    private static String body(String answer) {
        int end = answer.indexOf("\r\n\r\n");
        return end < 0 ? "" : answer.substring(end + 4);
    }

    /** {@code error} in words, such as {@code Connection refused}, or its kind when it has no message. */
    private static String describe(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static <T> List<T> results(List<Future<T>> checks) {
        return checks.stream().map(check -> {
            try {
                return check.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while testing Reachability", e);
            } catch (ExecutionException e) {
                throw new IllegalStateException("A Reachability check failed", e.getCause());
            }
        }).toList();
    }
}
