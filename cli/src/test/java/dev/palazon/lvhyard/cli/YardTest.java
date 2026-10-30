package dev.palazon.lvhyard.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code yard} command as a shell runs it: arguments in, exit code and output out. Behind it is a stub dashboard
 * that answers only what {@code openapi.yaml} describes.
 */
class YardTest {

    private final StubDashboard dashboard = new StubDashboard();

    @AfterEach
    void stopDashboard() {
        dashboard.close();
    }

    @Test
    void withoutSubcommandShowsUsage() {
        Run run = yard();

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).contains("Usage: yard").contains("--dashboard=<url>");
    }

    @Test
    void listShowsThisWorkstationThenTheOthersWithTheirStateHostnameInstancesAndHealth() {
        dashboard.answer("GET", "/api/yard", 200, """
                {"home": {"name": "ws-a", "services": [
                   {"workstation": "ws-a", "name": "api", "state": "unavailable", "publication": {"scope": "local"},
                    "hostname": "api.lvh.me", "warnings": [], "instances": []},
                   {"workstation": "ws-a", "name": "grafana", "state": "healthy", "publication": {"scope": "local"},
                    "hostname": "grafana.lvh.me", "warnings": [], "instances": [
                      {"id": "3f2a", "host": "10.1.2.3", "port": 3000, "health": "passing",
                       "tags": [], "meta": {}, "checks": [], "unreachable": false}]},
                   {"workstation": "ws-a", "name": "web", "state": "unpublished", "warnings": [], "instances": [
                      {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "critical",
                       "tags": [], "meta": {}, "checks": [], "unreachable": false},
                      {"id": "9d4e", "host": "10.1.2.3", "port": 8081, "health": "warning",
                       "tags": [], "meta": {}, "checks": [], "unreachable": false}]}]},
                 "remotes": [
                   {"name": "ws-b", "unreachable": false, "services": [
                     {"workstation": "ws-b", "name": "postgres", "state": "unhealthy",
                      "publication": {"scope": "local"}, "warnings": [], "instances": [
                        {"id": "5e6f", "host": "10.1.2.4", "port": 5432, "health": "critical",
                         "tags": [], "meta": {}, "checks": [], "unreachable": false}]}]},
                   {"name": "ws-c", "unreachable": true, "services": []},
                   {"name": "ws-d", "unreachable": false, "services": []}]}
                """);

        Run run = yard("list");

        assertThat(run.exitCode()).isZero();
        assertThat(run.lines()).containsExactly(
                "ws-a (this Workstation)",
                "  api: unavailable at api.lvh.me",
                "  grafana: healthy at grafana.lvh.me",
                "    10.1.2.3:3000  passing   3f2a",
                "  web: unpublished",
                "    10.1.2.3:8080  critical  7b1c",
                "    10.1.2.3:8081  warning   9d4e",
                "ws-b",
                "  postgres: unhealthy",
                "    10.1.2.4:5432  critical  5e6f",
                "ws-c: Workstation unreachable",
                "ws-d: no Services");
    }

    @Test
    void listWithWorkstationShowsThatWorkstationsServices() {
        dashboard.answer("GET", "/api/services?workstation=ws-b", 200, """
                [{"workstation": "ws-b", "name": "postgres", "state": "unpublished", "warnings": [], "instances": [
                    {"id": "5e6f", "host": "10.1.2.4", "port": 5432, "health": "passing",
                     "tags": [], "meta": {}, "checks": [], "unreachable": false}]}]
                """);

        Run run = yard("list", "--workstation", "ws-b");

        assertThat(run.exitCode()).isZero();
        assertThat(run.lines()).containsExactly(
                "ws-b",
                "  postgres: unpublished",
                "    10.1.2.4:5432  passing   5e6f");
    }

    @Test
    void listMarksAnInstanceThisWorkstationCannotReach() {
        dashboard.answer("GET", "/api/services?workstation=ws-b", 200, """
                [{"workstation": "ws-b", "name": "api", "state": "healthy", "publication": {"scope": "yard"},
                  "hostname": "api.ws-b.lvh.me", "yardHostname": "api.ws-b.lvh.me", "warnings": [], "instances": [
                    {"id": "1a2b", "host": "10.1.2.4", "port": 9090, "health": "passing",
                     "tags": [], "meta": {}, "checks": [], "unreachable": false},
                    {"id": "3c4d", "host": "127.0.0.1", "port": 9091, "health": "passing",
                     "tags": [], "meta": {}, "checks": [], "unreachable": true}]}]
                """);

        Run run = yard("list", "--workstation", "ws-b");

        assertThat(run.lines()).containsExactly(
                "ws-b",
                "  api: healthy at api.ws-b.lvh.me",
                "    10.1.2.4:9090  passing   1a2b",
                "    127.0.0.1:9091  passing   3c4d  unreachable from here: on ws-b's loopback, no Route");
    }

    @Test
    void listWithoutServicesSaysSo() {
        dashboard.answer("GET", "/api/yard", 200, """
                {"home": {"name": "ws-a", "services": []}, "remotes": []}
                """);
        dashboard.answer("GET", "/api/services?workstation=ws-b", 200, "[]");

        assertThat(yard("list").out()).isEqualTo("ws-a (this Workstation): no Services\n");
        assertThat(yard("list", "--workstation", "ws-b").out()).isEqualTo("ws-b: no Services\n");
    }

    @Test
    void listOfWorkstationNotInTheYardFailsWithTheReason() {
        dashboard.answer("GET", "/api/services?workstation=ws-typo", 404, """
                {"status": 404, "detail": "The Yard has no Workstation ws-typo."}
                """);

        Run run = yard("list", "--workstation", "ws-typo");

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.out()).isEmpty();
        assertThat(run.err()).contains("The Yard has no Workstation ws-typo.");
    }

    @Test
    void listOfUnreachableWorkstationFailsWithTheReason() {
        dashboard.answer("GET", "/api/services?workstation=ws-b", 503, """
                {"status": 503, "detail": "Workstation ws-b is unreachable: its Consul datacenter does not answer."}
                """);

        Run run = yard("list", "--workstation", "ws-b");

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.err()).contains("Workstation ws-b is unreachable: its Consul datacenter does not answer.");
    }

    @Test
    void registerAddsStaticInstanceAtHostAndPort() {
        dashboard.answer("POST", "/api/workstation/services/web/instances", 201, """
                {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "critical",
                 "tags": [], "meta": {}, "checks": [], "unreachable": false}
                """);

        Run run = yard("register", "web", "--host", "10.1.2.3", "--port", "8080");

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).isEqualTo("Registered web at 10.1.2.3:8080, Instance 7b1c.\n");
        dashboard.verifyCalled("POST", "/api/workstation/services/web/instances", """
                {"host": "10.1.2.3", "port": 8080}
                """);
    }

    @Test
    void registerWithoutHostLeavesItToTheDashboard() {
        dashboard.answer("POST", "/api/workstation/services/web/instances", 201, """
                {"id": "7b1c", "host": "192.168.1.20", "port": 8080, "health": "critical",
                 "tags": [], "meta": {}, "checks": [], "unreachable": false}
                """);

        Run run = yard("register", "web", "--port", "8080");

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).contains("at 192.168.1.20:8080");
        dashboard.verifyCalled("POST", "/api/workstation/services/web/instances", """
                {"port": 8080}
                """);
    }

    @Test
    void registerTakesTheCheckItsIntervalAndTimeoutTagsAndMeta() {
        dashboard.answer("POST", "/api/workstation/services/web/instances", 201, """
                {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "critical",
                 "check": {"type": "http", "path": "/health", "interval": "5s", "timeout": "1s"},
                 "tags": ["backend", "v2"], "meta": {"owner": "pablo", "team": "core"},
                 "checks": [{"name": "HTTP GET /health", "health": "critical", "output": ""}],
                 "unreachable": false}
                """);

        Run run = yard("register", "web", "--port", "8080", "--check", "http", "--path", "/health",
                "--interval", "5s", "--timeout", "1s", "--tag", "backend", "--tag", "v2", "--meta", "owner=pablo",
                "--meta", "team=core");

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).isEqualTo("Registered web at 10.1.2.3:8080, Instance 7b1c.\n");
        dashboard.verifyCalled("POST", "/api/workstation/services/web/instances", """
                {"port": 8080, "check": {"type": "http", "path": "/health", "interval": "5s", "timeout": "1s"},
                 "tags": ["backend", "v2"], "meta": {"owner": "pablo", "team": "core"}}
                """);
    }

    @Test
    void registerWithOnlyAPathLeavesTheRestOfTheCheckToTheDashboard() {
        dashboard.answer("POST", "/api/workstation/services/web/instances", 201, """
                {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "critical",
                 "check": {"type": "http", "path": "/health", "interval": "10s", "timeout": "2s"},
                 "tags": [], "meta": {}, "checks": [], "unreachable": false}
                """);

        Run run = yard("register", "web", "--port", "8080", "--path", "/health");

        assertThat(run.exitCode()).isZero();
        dashboard.verifyCalled("POST", "/api/workstation/services/web/instances", """
                {"port": 8080, "check": {"path": "/health"}}
                """);
    }

    @Test
    void registerWithAScriptCheckShowsWhyTheDashboardRefused() {
        dashboard.answer("POST", "/api/workstation/services/web/instances", 400, """
                {"status": 400, "detail": "Script checks are not offered: use an http or a tcp check."}
                """);

        Run run = yard("register", "web", "--port", "8080", "--check", "script");

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.err()).contains("Script checks are not offered");
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
        answerWebWithInstances("""
                {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "passing",
                 "tags": [], "meta": {}, "checks": [], "unreachable": false},
                {"id": "9d4e", "host": "10.1.2.3", "port": 8081, "health": "passing",
                 "tags": [], "meta": {}, "checks": [], "unreachable": false}""");
        dashboard.answer("DELETE", "/api/workstation/services/web/instances/9d4e", 204, null);

        Run run = yard("remove", "web", "--port", "8081");

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).isEqualTo("Removed web at 10.1.2.3:8081.\n");
        dashboard.verifyCalled("DELETE", "/api/workstation/services/web/instances/9d4e");
    }

    @Test
    void removeByHostAndPortPicksTheInstanceAtThatHost() {
        answerWebWithInstances("""
                {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "passing",
                 "tags": [], "meta": {}, "checks": [], "unreachable": false},
                {"id": "9d4e", "host": "10.1.2.4", "port": 8080, "health": "passing",
                 "tags": [], "meta": {}, "checks": [], "unreachable": false}""");
        dashboard.answer("DELETE", "/api/workstation/services/web/instances/9d4e", 204, null);

        Run run = yard("remove", "web", "--host", "10.1.2.4", "--port", "8080");

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).isEqualTo("Removed web at 10.1.2.4:8080.\n");
    }

    @Test
    void removeByPortOnMoreThanOneHostAsksForTheHost() {
        answerWebWithInstances("""
                {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "passing",
                 "tags": [], "meta": {}, "checks": [], "unreachable": false},
                {"id": "9d4e", "host": "10.1.2.4", "port": 8080, "health": "passing",
                 "tags": [], "meta": {}, "checks": [], "unreachable": false}""");

        Run run = yard("remove", "web", "--port", "8080");

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.err()).contains("10.1.2.3").contains("10.1.2.4").contains("--host");
    }

    @Test
    void removeByPortWithNoInstanceThereSaysSo() {
        answerWebWithInstances("""
                {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "passing",
                 "tags": [], "meta": {}, "checks": [], "unreachable": false}""");

        Run run = yard("remove", "web", "--port", "9090");

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.err()).isEqualTo("web has no Instance at port 9090.\n");
    }

    @Test
    void removeNeedsAnIdOrAPort() {
        Run run = yard("remove", "web");

        assertThat(run.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
        assertThat(run.err()).contains("--id").contains("--port");
    }

    @Test
    void publishPublishesWithLocalScopeAndShowsTheHostname() {
        dashboard.answer("PUT", "/api/workstation/services/web/publication", 200, """
                {"workstation": "ws-a", "name": "web", "state": "unhealthy", "publication": {"scope": "local"},
                 "hostname": "web.lvh.me", "warnings": [], "instances": [
                    {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "critical",
                     "tags": [], "meta": {}, "checks": [], "unreachable": false}]}
                """);

        Run run = yard("publish", "web");

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).isEqualTo("Published web at web.lvh.me, with Local Scope. It is unhealthy.\n");
        dashboard.verifyCalled("PUT", "/api/workstation/services/web/publication");
    }

    @Test
    void publishWithYardScopeSendsTheScope() {
        dashboard.answer("PUT", "/api/workstation/services/web/publication", 200, """
                {"workstation": "ws-a", "name": "web", "state": "healthy", "publication": {"scope": "yard"},
                 "hostname": "web.lvh.me", "yardHostname": "web.ws-a.lvh.me", "warnings": [], "instances": [
                    {"id": "7b1c", "host": "10.1.2.3", "port": 8080, "health": "passing",
                     "tags": [], "meta": {}, "checks": [], "unreachable": false}]}
                """);

        Run run = yard("publish", "web", "--scope", "yard");

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).isEqualTo("Published web at web.lvh.me, with Yard Scope; other Workstations reach it at"
                + " web.ws-a.lvh.me. It is healthy.\n");
        dashboard.verifyCalled("PUT", "/api/workstation/services/web/publication", """
                {"scope": "yard"}
                """);
    }

    @Test
    void publishPrintsTheServicesWarnings() {
        dashboard.answer("PUT", "/api/workstation/services/web/publication", 200, """
                {"workstation": "ws-a", "name": "web", "state": "healthy", "publication": {"scope": "yard"},
                 "hostname": "web.lvh.me", "warnings": [
                    {"kind": "loopback-instance",
                     "message": "The Instance at 127.0.0.1:8080 is on a loopback address."}],
                 "instances": [
                    {"id": "7b1c", "host": "127.0.0.1", "port": 8080, "health": "passing",
                     "tags": [], "meta": {}, "checks": [], "unreachable": false}]}
                """);

        Run run = yard("publish", "web", "--scope", "yard");

        assertThat(run.exitCode()).isZero();
        assertThat(run.err()).isEqualTo("Warning: The Instance at 127.0.0.1:8080 is on a loopback address.\n");
    }

    @Test
    void publishRefusesAnUnknownScope() {
        Run run = yard("publish", "web", "--scope", "world");

        assertThat(run.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE);
        assertThat(run.err()).contains("local").contains("yard");
    }

    @Test
    void unpublishRemovesThePublication() {
        dashboard.answer("DELETE", "/api/workstation/services/web/publication", 204, null);

        Run run = yard("unpublish", "web");

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).isEqualTo("Unpublished web. Its Instances stay registered.\n");
        dashboard.verifyCalled("DELETE", "/api/workstation/services/web/publication");
    }

    @Test
    void publishingUnknownServiceIsRefused() {
        dashboard.answer("PUT", "/api/workstation/services/wbe/publication", 404, """
                {"type": "about:blank", "title": "Not Found", "status": 404,
                 "detail": "This Workstation has no Service wbe."}
                """);

        Run run = yard("publish", "wbe");

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.err()).isEqualTo("This Workstation has no Service wbe.\n");
    }

    @Test
    void refusalIsPrintedWithItsReason() {
        dashboard.answer("POST", "/api/workstation/services/Web/instances", 400, """
                {"type": "about:blank", "title": "Bad Request", "status": 400,
                 "detail": "The Service name Web is not a single DNS label."}
                """);

        Run run = yard("register", "Web", "--port", "8080");

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.out()).isEmpty();
        assertThat(run.err()).isEqualTo("The Service name Web is not a single DNS label.\n");
    }

    @Test
    void dashboardThatDoesNotAnswerIsExplained() throws IOException {
        String nothingListens = "http://127.0.0.1:" + freePort();

        Run run = yardAsTyped("--dashboard", nothingListens, "list");

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.err()).contains("The dashboard is not running on this Workstation")
                .contains(nothingListens).contains("mise run dashboard");
    }

    @Test
    void serverThatIsNotTheDashboardIsExplained() throws IOException {
        HttpServer other = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        other.createContext("/", exchange -> {
            byte[] page = "<html>Not LVH Yard</html>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, page.length);
            exchange.getResponseBody().write(page);
            exchange.close();
        });
        other.start();
        try {
            Run run = yardAsTyped("--dashboard", "http://127.0.0.1:" + other.getAddress().getPort(), "list");

            assertThat(run.exitCode()).isNotZero();
            assertThat(run.err()).contains("is not the LVH Yard dashboard");
        } finally {
            other.stop(0);
        }
    }

    @Test
    void dashboardUrlComesFromTheEnvironment() throws Exception {
        dashboard.answer("GET", "/api/yard", 200, """
                {"home": {"name": "ws-a", "services": []}, "remotes": []}
                """);

        Run run = yardProcess(Map.of("YARD_DASHBOARD_URL", dashboard.url().toString()), "list");

        assertThat(run.exitCode()).isZero();
        assertThat(run.out()).isEqualTo("ws-a (this Workstation): no Services\n");
    }

    @Test
    void dashboardOptionWinsOverTheEnvironment() throws Exception {
        dashboard.answer("GET", "/api/yard", 200, """
                {"home": {"name": "ws-a", "services": []}, "remotes": []}
                """);

        Run run = yardProcess(Map.of("YARD_DASHBOARD_URL", "http://127.0.0.1:" + freePort()),
                "--dashboard", dashboard.url().toString(), "list");

        assertThat(run.exitCode()).isZero();
    }

    @Test
    void dashboardUrlDefaultsToLoopback() throws Exception {
        Run run = yardProcess(Map.of(), "--help");

        assertThat(run.out()).contains("else http://127.0.0.1:8780.");
    }

    private void answerWebWithInstances(String instances) {
        dashboard.answer("GET", "/api/workstation/services/web", 200,
                "{\"workstation\": \"ws-a\", \"name\": \"web\", \"state\": \"unpublished\", \"warnings\": [],"
                        + " \"instances\": [" + instances + "]}");
    }

    /** The output of one run of {@code yard}. */
    record Run(int exitCode, String out, String err) {

        List<String> lines() {
            return out.lines().toList();
        }
    }

    /** Runs {@code yard} against the stub dashboard. */
    private Run yard(String... args) {
        List<String> withDashboard = new ArrayList<>(List.of("--dashboard", dashboard.url().toString()));
        withDashboard.addAll(List.of(args));
        return yardAsTyped(withDashboard.toArray(String[]::new));
    }

    /**
     * Runs {@code yard} in a JVM of its own, as a shell does, with {@code environment} in place of any
     * {@code YARD_*} variables of this one.
     */
    private static Run yardProcess(Map<String, String> environment, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of(ProcessHandle.current().info().command().orElseThrow(),
                "-cp", System.getProperty("java.class.path"), Yard.class.getName()));
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().keySet().removeIf(name -> name.startsWith("YARD_"));
        builder.environment().putAll(environment);
        Process process = builder.start();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Run(process.waitFor(), out, err);
    }

    /** A loopback port nothing listens on. */
    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    /** Runs {@code yard} with exactly {@code args}. */
    private static Run yardAsTyped(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine command = Yard.commandLine();
        command.setOut(new PrintWriter(out));
        command.setErr(new PrintWriter(err));
        int exitCode = command.execute(args);
        return new Run(exitCode, out.toString(), err.toString());
    }
}
