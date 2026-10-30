package dev.palazon.lvhyard.cli;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The dashboard on this Workstation, through the REST API that {@code openapi.yaml} describes. Every failure, a
 * refusal or a dashboard that does not answer, is a {@link YardException} whose message is ready to print.
 */
final class Dashboard {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    /** How long an answer may take once connected; the dashboard reads only its local Consul. */
    private static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(10);

    private static final ObjectMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final URI url;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    /** {@code url} with a trailing /, so the API's paths resolve below any path it has. */
    Dashboard(URI url) {
        this.url = url.toString().endsWith("/") ? url : URI.create(url + "/");
    }

    /**
     * A Service, with its Home Workstation, its Instances and its state: unpublished, unavailable, unhealthy or
     * healthy. A Service without a Publication has none, and one without a Route on this Workstation no hostname.
     */
    record Service(String workstation, String name, String state, Publication publication, String hostname,
            List<Instance> instances) {
    }

    /**
     * A Workstation of the Yard with its Services. An unreachable one, whose datacenter does not answer, has none for
     * now.
     */
    record Workstation(String name, boolean unreachable, List<Service> services) {
    }

    /** A Service's Publication, with its Scope. */
    record Publication(String scope) {
    }

    /** One Instance of a Service, with its Health: passing, warning or critical. */
    record Instance(String id, String host, int port, String health) {

        /** {@code host:port}, where the Instance is reached. */
        String address() {
            return Dashboard.address(host, port);
        }
    }

    /**
     * How an Instance's Health is checked: its type, http or tcp, the path an HTTP check GETs, its interval and
     * timeout. A part that is {@code null} is left to the dashboard.
     */
    record Check(String type, String path, String interval, String timeout) {

        /** The parts that are given, by their names in the API. */
        Map<String, String> given() {
            Map<String, String> parts = new LinkedHashMap<>();
            parts.put("type", type);
            parts.put("path", path);
            parts.put("interval", interval);
            parts.put("timeout", timeout);
            parts.values().removeIf(Objects::isNull);
            return parts;
        }
    }

    /** {@code host:port}, with an IPv6 host in brackets so its colons are not read as the port's. */
    static String address(String host, int port) {
        return (host.contains(":") ? "[" + host + "]" : host) + ":" + port;
    }

    /** Every Workstation of the Yard with its Services, this one first. */
    List<Workstation> workstations() {
        return read(send(get("api/workstations")), new TypeReference<>() { });
    }

    /** The Services of {@code workstation}, as the Yard sees them. */
    List<Service> services(String workstation) {
        return read(send(get("api/services?workstation=" + URLEncoder.encode(workstation, StandardCharsets.UTF_8))),
                new TypeReference<>() { });
    }

    /** This Workstation's {@code service}. */
    Service ownService(String service) {
        return read(send(get("api/workstation/services/" + segment(service))), new TypeReference<>() { });
    }

    /**
     * Registers a Static Instance of this Workstation's {@code service} at {@code host} and {@code port}, with
     * {@code check}, {@code tags} and {@code meta}. With no host, the dashboard puts it at this Workstation's LAN
     * address, and the parts of the check left out get the dashboard's defaults.
     */
    Instance register(String service, String host, int port, Check check, List<String> tags,
            Map<String, String> meta) {
        Map<String, Object> instance = new LinkedHashMap<>();
        if (host != null) {
            instance.put("host", host);
        }
        instance.put("port", port);
        if (!check.given().isEmpty()) {
            instance.put("check", check.given());
        }
        if (!tags.isEmpty()) {
            instance.put("tags", tags);
        }
        if (!meta.isEmpty()) {
            instance.put("meta", meta);
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(instancesUrl(service))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(instance)));
        return read(send(request), new TypeReference<>() { });
    }

    /** Removes the Static Instance {@code id} of this Workstation's {@code service}. */
    void remove(String service, String id) {
        send(HttpRequest.newBuilder(URI.create(instancesUrl(service) + "/" + segment(id))).DELETE());
    }

    /** Publishes this Workstation's {@code service} with Local Scope, and answers the published Service. */
    Service publish(String service) {
        return read(send(HttpRequest.newBuilder(publicationUrl(service)).PUT(HttpRequest.BodyPublishers.noBody())),
                new TypeReference<>() { });
    }

    /** Unpublishes this Workstation's {@code service}, which keeps its Instances. */
    void unpublish(String service) {
        send(HttpRequest.newBuilder(publicationUrl(service)).DELETE());
    }

    private URI publicationUrl(String service) {
        return url.resolve("api/workstation/services/" + segment(service) + "/publication");
    }

    private URI instancesUrl(String service) {
        return url.resolve("api/workstation/services/" + segment(service) + "/instances");
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(url.resolve(path)).GET();
    }

    /** {@code value} as one segment of a path, so a / or a space in a name cannot change the path. */
    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** The JSON body of {@code response}, which a server that is not the dashboard may not have. */
    private <T> T read(HttpResponse<String> response, TypeReference<T> type) {
        try {
            return JSON.readValue(response.body(), type);
        } catch (JacksonException e) {
            throw new YardException("What answers at " + url + " is not the LVH Yard dashboard: its answer is not"
                    + " the API's JSON.");
        }
    }

    /** Sends {@code request}, and answers its response if it is a success. */
    private HttpResponse<String> send(HttpRequest.Builder request) {
        HttpResponse<String> response;
        try {
            response = http.send(request.header("Accept", "application/json").timeout(ANSWER_TIMEOUT).build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (ConnectException | HttpConnectTimeoutException e) {
            throw new YardException("The dashboard is not running on this Workstation: nothing answers at "
                    + url + ". Start it with: mise run dashboard");
        } catch (HttpTimeoutException e) {
            throw new YardException("The dashboard at " + url + " did not answer within "
                    + ANSWER_TIMEOUT.toSeconds() + " s. Is its Consul running? Start it with: mise run up");
        } catch (IOException e) {
            throw new YardException("The dashboard at " + url + " could not be reached: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new YardException("Interrupted while waiting for the dashboard at " + url + ".");
        }
        if (response.statusCode() >= 400) {
            throw new YardException(reason(response));
        }
        return response;
    }

    /** Why the dashboard refused: the Problem Details' detail, or else its title, or else the status. */
    private static String reason(HttpResponse<String> response) {
        try {
            JsonNode problem = JSON.readTree(response.body());
            for (String member : List.of("detail", "title")) {
                if (problem.path(member).isString() && !problem.get(member).asString().isBlank()) {
                    return problem.get(member).asString();
                }
            }
        } catch (JacksonException e) {
            // Not a Problem Details document: the status is all there is.
        }
        return "The dashboard refused with HTTP status " + response.statusCode() + ".";
    }
}
