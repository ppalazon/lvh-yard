package dev.palazon.lvhyard.cli;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/**
 * {@code lvh-yard init}: writes a Workstation's {@code compose.yaml}, {@code .env} and {@code
 * route-profiles.yaml} from the templates inside the CLI ({@link Templates}), fills in every value
 * it can find, and prints the next steps. It never talks to the dashboard.
 */
@Command(
    name = "init",
    mixinStandardHelpOptions = true,
    description =
        "Writes compose.yaml, .env and route-profiles.yaml for this Workstation, ready for docker"
            + " compose up -d, and prints the next steps.")
class Init implements Runnable {

  /** The dashboard's rule for a Workstation name: one DNS label, lowercase. */
  private static final Pattern WORKSTATION = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");

  private static final Pattern DOMAIN =
      Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*");

  @Option(
      names = "--dir",
      paramLabel = "<path>",
      description = {
        "The folder to write the files to.",
        "Default: $XDG_CONFIG_HOME/lvh-yard, else ~/.config/lvh-yard."
      })
  Path dir;

  @Option(
      names = "--workstation",
      paramLabel = "<name>",
      description = "This Workstation's name: unique in the Yard, lowercase letters, digits and -.")
  String workstation;

  @Option(
      names = "--peers",
      paramLabel = "<address>",
      split = "[,\\s]+",
      description =
          "The other Workstations' LAN addresses, each <ip> or <ip>:<serf-wan-port>. None for the"
              + " first Workstation of a Yard.")
  List<String> peers;

  @Option(
      names = "--gossip-key",
      paramLabel = "<key>",
      description =
          "The Yard's gossip key, the same on every Workstation. Default: a new one, for the first"
              + " Workstation of a Yard.")
  String gossipKey;

  @Option(
      names = "--base-domain",
      paramLabel = "<domain>",
      description = "The Yard's base domain, the same on every Workstation. Default: lvh.me.")
  String baseDomain;

  /** What to do with files already there. At most one: picocli refuses a second (exit 2). */
  @ArgGroup(exclusive = true, multiplicity = "0..1")
  Mode mode = new Mode();

  /** The ways to change files already there; a new one is one more option here. */
  static class Mode {

    @Option(names = "--force", description = "Overwrite the files when they exist.")
    boolean force;

    @Option(
        names = "--upgrade",
        description =
            "Move the consul, traefik and dashboard images in the existing compose.yaml to this"
                + " version's, and change nothing else.")
    boolean upgrade;
  }

  /** The socket whose group the dashboard needs to read Docker. Hidden: a test points it away. */
  @Option(names = "--docker-socket", hidden = true, defaultValue = "/var/run/docker.sock")
  Path dockerSocket;

  /**
   * Asks as at a terminal, reading the answers from standard input. Hidden: a test types them in.
   */
  @Option(names = "--terminal", hidden = true)
  boolean terminal;

  @Spec CommandSpec spec;

  /** The services whose images {@code --upgrade} moves to this version's. */
  private static final List<String> UPGRADED = List.of("consul", "traefik", "dashboard");

  /** The answers typed in at the terminal, read once a question is asked. */
  private BufferedReader answers;

  @Override
  public void run() {
    Path folder = dir != null ? dir : ConfigFolder.standard(System.getenv());
    if (mode.upgrade) {
      upgrade(folder);
      return;
    }
    String name = workstation != null ? workstation : askWorkstation();
    if (!WORKSTATION.matcher(name).matches()) {
      throw usage(
          "--workstation "
              + name
              + " is not a Workstation name: use lowercase letters, digits and -, at most 63, not"
              + " starting or ending with -.");
    }
    if (baseDomain != null && !DOMAIN.matcher(baseDomain).matches()) {
      throw usage("--base-domain " + baseDomain + " is not a domain name such as lvh.me.");
    }
    List<String> peerAddresses = peers != null ? peers : atTerminal() ? askPeers() : List.of();
    String key = gossipKey != null ? checkedGossipKey(gossipKey) : newGossipKey();

    PrintWriter out = spec.commandLine().getOut();
    PrintWriter err = spec.commandLine().getErr();
    EnvFile env =
        Templates.env()
            .set("YARD_WORKSTATION", name)
            .set("YARD_GOSSIP_KEY", key)
            .set("YARD_PEERS", String.join(" ", peerAddresses))
            .set("COMPOSE_PROFILES", "dashboard");
    if (baseDomain != null) {
      env.set("YARD_BASE_DOMAIN", baseDomain);
    }
    id("-u").ifPresent(uid -> env.set("YARD_UID", uid));
    id("-g").ifPresent(gid -> env.set("YARD_GID", gid));
    Optional<String> dockerGid = dockerGid();
    dockerGid.ifPresent(gid -> env.set("YARD_DOCKER_GID", gid));

    Map<String, String> files = new LinkedHashMap<>();
    files.put(Templates.COMPOSE, Templates.compose());
    files.put(Templates.ENV, env.text());
    files.put(Templates.ROUTE_PROFILES, Templates.routeProfiles());
    write(folder, files);

    if (dockerGid.isEmpty()) {
      err.println(
          "Warning: no Docker socket at "
              + dockerSocket
              + ", so YARD_DOCKER_GID in .env is not set. The dashboard from its image will not"
              + " start, nor follow containers, until it is: stat -c %g /var/run/docker.sock.");
      err.flush();
    }
    out.println("Wrote " + String.join(", ", files.keySet()) + " to " + folder + ".");
    out.println();
    out.println("The Yard's gossip key: " + key);
    out.println(
        "Every Workstation of the Yard needs this same key: give it to the next one with"
            + " lvh-yard init --gossip-key.");
    out.println();
    out.print(nextSteps(folder, env));
    out.flush();
  }

  /**
   * Moves the images of {@link #UPGRADED} in {@code folder}'s {@code compose.yaml} to the
   * template's, by line. When one has no {@code image:} line, the file is left as it is.
   */
  private void upgrade(Path folder) {
    Path file = folder.resolve(Templates.COMPOSE);
    String text;
    try {
      text = Files.readString(file);
    } catch (NoSuchFileException e) {
      throw new YardException(
          file + " does not exist: nothing to upgrade. Write it first with lvh-yard init.");
    } catch (IOException e) {
      throw new YardException("Could not read " + file + ": " + e.getMessage());
    }
    ComposeImages compose = ComposeImages.parse(text);
    List<String> missing = compose.missing(UPGRADED);
    if (!missing.isEmpty()) {
      throw new YardException(
          file
              + " has no image: line for "
              + String.join(", ", missing)
              + ": nothing was changed. Change its images by hand.");
    }
    ComposeImages template = ComposeImages.parse(Templates.compose());
    PrintWriter out = spec.commandLine().getOut();
    for (String service : UPGRADED) {
      String from = compose.image(service).orElseThrow();
      String to = template.image(service).orElseThrow();
      if (from.equals(to)) {
        out.println(service + ": " + to + ", already this version's");
      } else {
        compose.setImage(service, to);
        out.println(service + ": " + from + " -> " + to);
      }
    }
    try {
      Files.writeString(file, compose.text());
    } catch (IOException e) {
      throw new YardException("Could not write " + file + ": " + e.getMessage());
    }
    out.println(
        "Upgraded "
            + file
            + "; .env and route-profiles.yaml are untouched. Start the new images: cd "
            + folder
            + " && docker compose up -d");
    out.flush();
  }

  /** What to do once the files are written, with the ports and addresses {@code env} sets. */
  private static String nextSteps(Path folder, EnvFile env) {
    String server = env.get("YARD_CONSUL_SERVER_PORT").orElse("8300");
    String wan = env.get("YARD_CONSUL_SERF_WAN_PORT").orElse("8302");
    String bind = env.get("YARD_DASHBOARD_BIND_ADDRESS").orElse("127.0.0.1");
    String host = bind.isEmpty() || bind.equals("0.0.0.0") ? "127.0.0.1" : bind;
    String port = env.get("YARD_DASHBOARD_PORT").orElse("7780");
    return """
    Next steps:
      1. Let the other Workstations reach this one on TCP port %s and on TCP and UDP port %s
         (Consul's server and WAN ports).
      2. Start this Workstation: cd %s && docker compose up -d
      3. Open the dashboard: http://%s:%s
      4. Check that everything runs: lvh-yard status
    """
        .formatted(server, wan, folder, host, port);
  }

  /** The Workstation name typed in, asked again until it is one. */
  private String askWorkstation() {
    PrintWriter out = spec.commandLine().getOut();
    while (true) {
      String name =
          ask(
              "Workstation name, unique in the Yard (lowercase letters, digits and -): ",
              "--workstation");
      if (WORKSTATION.matcher(name).matches()) {
        return name;
      }
      out.println(
          (name.isEmpty() ? "A Workstation needs a name" : name + " is not a Workstation name")
              + ": use lowercase letters, digits and -, at most 63, not starting or ending with"
              + " -.");
    }
  }

  /** The peers typed in: none for the first Workstation of a Yard. */
  private List<String> askPeers() {
    String typed =
        ask(
            "Peers: the other Workstations' LAN addresses, each <ip> or <ip>:<serf-wan-port>,"
                + " separated by spaces (none for the first Workstation of a Yard): ",
            "--peers");
    return typed.isEmpty() ? List.of() : List.of(typed.split("[,\\s]+"));
  }

  /**
   * The line typed in after {@code question}, stripped. Without a terminal, or once the input ends,
   * a usage error that names {@code flag}, as no one is there to answer.
   */
  private String ask(String question, String flag) {
    if (!atTerminal()) {
      throw usage("Missing " + flag + ": no terminal to ask for it, so give it as " + flag + ".");
    }
    PrintWriter out = spec.commandLine().getOut();
    out.print(question);
    out.flush();
    try {
      if (answers == null) {
        answers = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
      }
      String line = answers.readLine();
      if (line == null) {
        out.println();
        throw usage("Missing " + flag + ": the input ended before it was given.");
      }
      return line.strip();
    } catch (IOException e) {
      throw usage("Missing " + flag + ": could not read it: " + e.getMessage());
    }
  }

  /** Whether someone is there to answer questions: a terminal, or a test typing in for one. */
  private boolean atTerminal() {
    return terminal || interactive();
  }

  /** Whether a person is at a terminal to answer questions. */
  static boolean interactive() {
    Console console = System.console();
    return console != null && console.isTerminal();
  }

  private ParameterException usage(String message) {
    return new ParameterException(spec.commandLine(), message);
  }

  /** A key as {@code consul keygen} makes one: 32 random bytes, Base64-encoded. */
  private static String newGossipKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  /** {@code key}, when Consul accepts it: Base64 of 16, 24 or 32 bytes. */
  private String checkedGossipKey(String key) {
    try {
      int length = Base64.getDecoder().decode(key).length;
      if (length == 16 || length == 24 || length == 32) {
        return key;
      }
    } catch (IllegalArgumentException notBase64) {
      // Explained below.
    }
    throw usage(
        "--gossip-key is not a gossip key: copy the Yard's key, Base64 of 32 bytes, from another"
            + " Workstation's .env.");
  }

  /** The group that owns the Docker socket, when there is one. */
  private Optional<String> dockerGid() {
    try {
      return Optional.of(String.valueOf(Files.getAttribute(dockerSocket, "unix:gid")));
    } catch (IOException | UnsupportedOperationException noSocket) {
      return Optional.empty();
    }
  }

  /** What {@code id <flag>} prints for the user running this command, when it can be run. */
  private static Optional<String> id(String flag) {
    try {
      Process id = new ProcessBuilder("id", flag).redirectErrorStream(true).start();
      String printed = new String(id.getInputStream().readAllBytes()).strip();
      return id.waitFor() == 0 && printed.matches("\\d+") ? Optional.of(printed) : Optional.empty();
    } catch (IOException e) {
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }

  /**
   * Writes each of {@code files} to {@code folder}. Without {@code --force}, none is written when
   * any already exists. {@code .env} is readable by its owner only: it holds the gossip key.
   */
  private void write(Path folder, Map<String, String> files) {
    if (!mode.force) {
      List<String> existing =
          files.keySet().stream()
              .filter(name -> Files.exists(folder.resolve(name)))
              .map(name -> folder.resolve(name).toString())
              .toList();
      if (!existing.isEmpty()) {
        throw new YardException(
            String.join(", ", existing)
                + (existing.size() == 1 ? " already exists" : " already exist")
                + ": nothing was written. Give --force to overwrite.");
      }
    }
    Path current = null;
    try {
      Files.createDirectories(folder);
      for (Map.Entry<String, String> file : files.entrySet()) {
        current = folder.resolve(file.getKey());
        if (mode.force) {
          Files.writeString(current, file.getValue());
        } else {
          Files.writeString(current, file.getValue(), StandardOpenOption.CREATE_NEW);
        }
        if (file.getKey().equals(Templates.ENV)) {
          ownerOnly(current);
        }
      }
    } catch (FileAlreadyExistsException e) {
      throw new YardException(e.getFile() + " already exists. Give --force to overwrite.");
    } catch (IOException e) {
      throw new YardException(
          "Could not write " + (current != null ? current : folder) + ": " + e.getMessage());
    }
  }

  private static void ownerOnly(Path file) throws IOException {
    try {
      Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
    } catch (UnsupportedOperationException notPosix) {
      // A file system without POSIX permissions keeps its own.
    }
  }
}
