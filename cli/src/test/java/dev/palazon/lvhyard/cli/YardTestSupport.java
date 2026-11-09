package dev.palazon.lvhyard.cli;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** What the CLI tests share: the stub dashboard, the ways to run lvh-yard, and file helpers. */
abstract class YardTestSupport {

  /**
   * The native executable to test in place of the classes, as {@code -Pnative} builds it: every
   * test then runs it in a process of its own. Unset, the tests run the classes in this JVM.
   */
  static final String NATIVE_YARD = System.getProperty("yard.executable");

  final StubDashboard dashboard = new StubDashboard();

  /**
   * The HOME of every {@link #yardProcess}, empty unless a test writes to it, so no test reads the
   * real user's {@code ~/.config/lvh-yard/.env}.
   */
  @TempDir Path home;

  @AfterEach
  void stopDashboard() {
    dashboard.close();
  }

  void catalogAnswer() {
    dashboard.answer(
        "GET",
        "/api/yard",
        200,
        """
        {"home": {"name": "ws-a", "services": [
          {"workstation": "ws-a", "name": "web", "state": "degraded", "created": true, "empty": false,
           "hostname": "web.lvh.me", "containerBindings": [],
           "warnings": [
             {"kind": "loopback-instance", "severity": "risk", "message": "Hidden warning."},
             {"kind": "unknown-route-profile", "severity": "error", "message": "Missing Route Profile."}],
           "instances": [
             {"id": "hidden-good", "host": "10.1.2.3", "port": 8080, "health": "passing",
              "tags": [], "meta": {}, "checks": [], "outOfRange": true},
             {"id": "hidden-bad", "host": "127.0.0.1", "port": 8081, "health": "critical",
              "tags": [], "meta": {}, "checks": [], "outOfRange": false},
             {"id": "hidden-warning", "host": "10.1.2.3", "port": 8082, "health": "warning",
              "tags": [], "meta": {}, "checks": [], "outOfRange": false}]},
          {"workstation": "ws-a", "name": "worker", "state": "unpublished", "created": true, "empty": false,
           "containerBindings": [], "warnings": [], "instances": [
             {"id": "worker-critical", "host": "10.1.2.3", "port": 8083, "health": "critical",
              "tags": [], "meta": {}, "checks": [], "outOfRange": false},
             {"id": "worker-warning", "host": "10.1.2.3", "port": 8084, "health": "warning",
              "tags": [], "meta": {}, "checks": [], "outOfRange": false}]}]},
         "remotes": [
          {"name": "ws-b", "unreachable": false, "services": [
            {"workstation": "ws-b", "name": "database", "state": "healthy", "created": true, "empty": false,
             "hostname": "db.lvh.me", "containerBindings": [], "warnings": [], "instances": [
               {"id": "db-first", "host": "10.1.2.4", "port": 5432, "health": "passing",
                "tags": [], "meta": {}, "checks": [], "outOfRange": false},
               {"id": "db-second", "host": "10.1.2.4", "port": 5433, "health": "passing",
                "tags": [], "meta": {}, "checks": [], "outOfRange": false}]},
            {"workstation": "ws-b", "name": "cache", "state": "unavailable", "created": true, "empty": true,
             "containerBindings": [], "warnings": [], "instances": []}]},
          {"name": "ws-c", "unreachable": true, "services": []},
          {"name": "ws-d", "unreachable": false, "services": []}]}
        """);
  }

  void standaloneContainersAnswer() {
    dashboard.answer(
        "GET",
        "/api/workstation/containers",
        200,
        """
        [{"name": "worker-1", "service": "worker", "ports": []},
         {"name": "metrics-2", "service": "metrics", "health": "starting",
          "ports": [{"containerPort": 9090}]},
         {"name": "database-1", "service": "database", "ports": [{"containerPort": 5432}]},
         {"name": "metrics-1", "service": "metrics",
          "ports": [{"containerPort": 8080, "hostIp": "::", "hostPort": 37137},
                    {"containerPort": 8080, "hostIp": "127.0.0.1", "hostPort": 37138},
                    {"containerPort": 8080, "hostIp": "0.0.0.0", "hostPort": 37137}]}]
        """);
  }

  void replicaContainersAnswer() {
    dashboard.answer(
        "GET",
        "/api/workstation/containers",
        200,
        """
        [{"name": "lvh-yard-test-whoami-3", "composeProject": "lvh-yard-test",
          "composeService": "whoami", "service": "whoami",
          "ports": [{"containerPort": 80, "hostIp": "0.0.0.0", "hostPort": 37143}]},
         {"name": "lvh-yard-test-http-https-echo-2", "composeProject": "lvh-yard-test",
          "composeService": "http-https-echo", "service": "http-https-echo",
          "ports": [{"containerPort": 8443, "hostIp": "0.0.0.0", "hostPort": 37140},
                    {"containerPort": 8080, "hostIp": "0.0.0.0", "hostPort": 37139}]},
         {"name": "lvh-yard-test-whoami-1", "composeProject": "lvh-yard-test",
          "composeService": "whoami", "service": "whoami",
          "ports": [{"containerPort": 80, "hostIp": "0.0.0.0", "hostPort": 37141}]},
         {"name": "lvh-yard-test-http-https-echo-1", "composeProject": "lvh-yard-test",
          "composeService": "http-https-echo", "service": "http-https-echo", "health": "healthy",
          "ports": [{"containerPort": 8080, "hostIp": "0.0.0.0", "hostPort": 37137},
                    {"containerPort": 8443, "hostIp": "0.0.0.0", "hostPort": 37138}]},
         {"name": "lvh-yard-test-whoami-2", "composeProject": "lvh-yard-test",
          "composeService": "whoami", "service": "whoami",
          "ports": [{"containerPort": 80, "hostIp": "0.0.0.0", "hostPort": 37142}]}]
        """);
  }

  static final String BACKUP =
      """
      version: 1
      exported-from:
        workstation: ws-a
        at: '2026-11-05T10:00:00Z'
      route-profiles: {}
      services:
        web:
          publication: none
          static-instances:
            - port: 8080
      aliases: {}
      """;

  static final String PLAN =
      """
      {"exportedFrom": {"workstation": "ws-a", "at": "2026-11-05T10:00:00Z"},
       "mode": "add", "dryRun": %s,
       "entries": [
         {"kind": "route-profile", "name": "gzip", "action": "add"},
         {"kind": "service", "name": "web", "action": "skip", "reason": "It exists already."},
         {"kind": "static-instance", "name": "10.1.2.3:8080", "service": "web", "action": "add"},
         {"kind": "publication", "name": "web", "action": "skip",
          "reason": "It exists already."}]}
      """;

  static final String PLAN_LINES =
      """
      Backup of ws-a at 2026-11-05T10:00:00Z.
      add Route Profile gzip
      skip Service web: It exists already.
      add Static Instance 10.1.2.3:8080 of web
      skip Publication of web: It exists already.
      """;

  /** {@code GET} of {@code team} answers a headers profile that sets {@code X-Team: a}. */
  void answerTeamProfile() {
    dashboard.answer(
        "GET",
        "/api/workstation/route-profiles/team",
        200,
        """
        {"name": "team", "type": "headers", "services": [],
         "definition": {"headers": {"customRequestHeaders": {"X-Team": "a"}}}}
        """);
  }

  void answerTeamProfileSaved() {
    dashboard.answer(
        "PUT",
        "/api/workstation/route-profiles/team",
        200,
        """
        {"name": "team", "type": "headers", "services": [],
         "definition": {"headers": {"customRequestHeaders": {"X-Team": "b"}}}}
        """);
  }

  /**
   * The value of the first {@code image:} line in {@code compose} that starts with {@code prefix}.
   */
  static String imageOf(String compose, String prefix) {
    return compose
        .lines()
        .map(String::strip)
        .filter(line -> line.startsWith("image: " + prefix))
        .map(line -> line.substring("image: ".length()))
        .findFirst()
        .orElseThrow();
  }

  void answerWebWithBindings(String bindings) {
    dashboard.answer(
        "GET",
        "/api/workstation/services/web",
        200,
        "{\"workstation\": \"ws-a\", \"name\": \"web\", \"state\": \"unpublished\", \"created\":"
            + " true, \"empty\": false, \"warnings\": [], \"instances\": [], \"containerBindings\":"
            + " ["
            + bindings
            + "]}");
  }

  void answerWebWithInstances(String instances) {
    dashboard.answer(
        "GET",
        "/api/workstation/services/web",
        200,
        "{\"workstation\": \"ws-a\", \"name\": \"web\", \"state\": \"unpublished\", \"created\":"
            + " true, \"empty\": false, \"warnings\": [], \"containerBindings\": [], \"instances\":"
            + " ["
            + instances
            + "]}");
  }

  /** The lines of the {@code .env} that {@code init} wrote in {@code dir}. */
  static List<String> env(Path dir) throws IOException {
    return Files.readAllLines(dir.resolve(".env"));
  }

  /** The {@code .bak} files in {@code dir}. */
  static List<Path> backups(Path dir) throws IOException {
    try (var files = Files.list(dir)) {
      return files.filter(path -> path.getFileName().toString().endsWith(".bak")).toList();
    }
  }

  /** The value of {@code key} in the {@code .env} that {@code init} wrote in {@code dir}. */
  static String setting(Path dir, String key) throws IOException {
    return env(dir).stream()
        .filter(line -> line.startsWith(key + "="))
        .map(line -> line.substring(key.length() + 1))
        .findFirst()
        .orElseThrow();
  }

  void answerAnEmptyYard() {
    dashboard.answer(
        "GET",
        "/api/yard",
        200,
        """
        {"home": {"name": "ws-a", "services": []}, "remotes": []}
        """);
  }

  /**
   * Sets {@code key} to {@code value} in the {@code .env} that {@code init} wrote in {@code dir}.
   */
  static void setInEnv(Path dir, String key, String value) throws IOException {
    Path env = dir.resolve(".env");
    Files.writeString(env, EnvFile.parse(Files.readString(env)).set(key, value).text());
  }

  /** Whether this computer has {@code docker compose}. */
  static boolean dockerCompose() {
    try {
      return run(new ProcessBuilder("docker", "compose", "version")).exitCode() == 0;
    } catch (IOException | InterruptedException e) {
      return false;
    }
  }

  /** The output of one run of {@code lvh-yard}. */
  record Run(int exitCode, String out, String err) {

    List<String> lines() {
      return out.lines().toList();
    }
  }

  /** Runs {@code lvh-yard} against the stub dashboard. */
  Run yard(String... args) {
    List<String> withDashboard =
        new ArrayList<>(List.of("--dashboard", dashboard.url().toString()));
    withDashboard.addAll(List.of(args));
    return yardAsTyped(withDashboard.toArray(String[]::new));
  }

  /**
   * Runs {@code lvh-yard} with {@code environment} as its environment, and {@link #home} as HOME
   * unless {@code environment} names another. With the native executable, in a process of its own.
   */
  Run yardProcess(Map<String, String> environment, String... args) throws Exception {
    if (NATIVE_YARD != null) {
      return realProcess(environment, args);
    }
    return inMemory(new FakeShell(withHome(environment)), args);
  }

  /**
   * Runs {@code lvh-yard} in a process of its own, as a shell does, with {@code environment} in
   * place of any {@code YARD_*} variables of this one, and {@link #home} as HOME unless {@code
   * environment} names another.
   */
  Run realProcess(Map<String, String> environment, String... args) throws Exception {
    ProcessBuilder builder = new ProcessBuilder(command(args));
    builder
        .environment()
        .keySet()
        .removeIf(name -> name.startsWith("YARD_") || name.equals("XDG_CONFIG_HOME"));
    builder.environment().put("HOME", home.toString());
    builder.environment().putAll(environment);
    return run(builder);
  }

  /**
   * Runs {@code lvh-yard} against the stub dashboard with {@code editor}, which changes the text of
   * the file, as $EDITOR, and {@code input} on its standard input. Only in this JVM: the native
   * executable cannot run a Java editor.
   */
  Run yardWithEditor(UnaryOperator<String> editor, String input, String... args) {
    assumeTrue(NATIVE_YARD == null, "A Java editor runs only in this JVM");
    List<String> withDashboard =
        new ArrayList<>(List.of("--dashboard", dashboard.url().toString()));
    withDashboard.addAll(List.of(args));
    return inMemory(
        new FakeShell(withHome(Map.of("EDITOR", "test-editor")), input, editor),
        withDashboard.toArray(String[]::new));
  }

  /** Runs {@code lvh-yard} with {@code input} on its standard input. */
  Run yardWithInput(String input, String... args) throws Exception {
    if (NATIVE_YARD != null) {
      Process process = new ProcessBuilder(command(args)).start();
      try (var in = process.getOutputStream()) {
        in.write(input.getBytes(StandardCharsets.UTF_8));
      }
      String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
      return new Run(process.waitFor(), out, err);
    }
    return inMemory(new FakeShell(withHome(Map.of()), input, text -> text), args);
  }

  /** Runs {@code lvh-yard} as if at a terminal, with {@code answers} typed in. */
  Run yardAtTerminal(String answers, String... args) throws Exception {
    List<String> withTerminal = new ArrayList<>(List.of(args));
    withTerminal.add("--terminal");
    return yardWithInput(answers, withTerminal.toArray(String[]::new));
  }

  private Map<String, String> withHome(Map<String, String> environment) {
    Map<String, String> all = new HashMap<>();
    all.put("HOME", home.toString());
    all.putAll(environment);
    return all;
  }

  /**
   * {@code lvh-yard} with {@code args}: the native executable when given, else a JVM of its own.
   */
  static List<String> command(String... args) {
    List<String> command =
        NATIVE_YARD != null
            ? new ArrayList<>(List.of(NATIVE_YARD))
            : new ArrayList<>(
                List.of(
                    ProcessHandle.current().info().command().orElseThrow(),
                    "-cp",
                    System.getProperty("java.class.path"),
                    Yard.class.getName()));
    command.addAll(List.of(args));
    return command;
  }

  static Run run(ProcessBuilder builder) throws IOException, InterruptedException {
    Process process = builder.start();
    String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    return new Run(process.waitFor(), out, err);
  }

  /** A loopback port nothing listens on. */
  static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      return socket.getLocalPort();
    }
  }

  /** Runs {@code lvh-yard} with exactly {@code args}, and {@link #home} as HOME. */
  Run yardAsTyped(String... args) {
    if (NATIVE_YARD != null) {
      try {
        return run(new ProcessBuilder(command(args)));
      } catch (IOException | InterruptedException e) {
        throw new IllegalStateException("Could not run " + NATIVE_YARD, e);
      }
    }
    return inMemory(new FakeShell(withHome(Map.of())), args);
  }

  /** Runs {@code lvh-yard} in this JVM, with {@code shell} as its process. */
  static Run inMemory(FakeShell shell, String... args) {
    StringWriter out = new StringWriter();
    StringWriter err = new StringWriter();
    CommandLine command = Yard.commandLine(shell);
    command.setOut(new PrintWriter(out));
    command.setErr(new PrintWriter(err));
    int exitCode = command.execute(args);
    return new Run(exitCode, out.toString(), err.toString());
  }
}
