package dev.palazon.lvhyard.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** Writes the files from {@link Templates} and never talks to the dashboard. */
@Command(
    name = "init",
    mixinStandardHelpOptions = true,
    description = "Writes the configuration files for this Workstation.")
class Init implements Runnable {

  @ParentCommand Yard yard;

  /** The dashboard's rule for a Workstation name: one DNS label, lowercase. */
  private static final Pattern WORKSTATION = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");

  private static final Pattern DOMAIN =
      Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*");

  private static final String PEER_SEPARATORS = "[,\\s]+";

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
      split = PEER_SEPARATORS,
      description =
          "The LAN addresses of the other Workstations, as <ip> or <ip>:<serf-wan-port>. Leave out"
              + " for the first one.")
  List<String> peers;

  @Option(
      names = "--gossip-key",
      paramLabel = "<key>",
      description = "The Yard's gossip key, the same on every Workstation (default: a new key).")
  String gossipKey;

  @Option(
      names = "--base-domain",
      paramLabel = "<domain>",
      description =
          "The domain at the end of each hostname this Workstation routes (default: lvh.me).")
  String baseDomain;

  /** What to do with files already there. At most one: picocli refuses a second (exit 2). */
  @ArgGroup(exclusive = true, multiplicity = "0..1")
  Mode mode = new Mode();

  static class Mode {

    @Option(names = "--force", description = "Overwrite the files when they exist.")
    boolean force;

    @Option(
        names = "--upgrade",
        description =
            "Only update the consul, traefik and dashboard images in compose.yaml to this version.")
    boolean upgrade;

    @Option(
        names = "--reinit",
        description = {
          "Rewrite compose.yaml and .env from this version's templates, with the old .env values.",
          "A flag given with it replaces the old value.",
          "Keeps the old files as <file>.<yyyyMMdd-HHmmss>.bak."
        })
    boolean reinit;
  }

  /** The socket whose group the dashboard needs to read Docker. Hidden: a test points it away. */
  @Option(names = "--docker-socket", hidden = true, defaultValue = "/var/run/docker.sock")
  Path dockerSocket;

  /** Hidden: a test gives the hostname that init offers as the Workstation name. */
  @Option(names = "--hostname", hidden = true)
  String hostname;

  /** Hidden: a test types the answers in. */
  @Option(names = "--terminal", hidden = true)
  boolean terminal;

  @Spec CommandSpec spec;

  private BufferedReader answers;

  @Override
  public void run() {
    Path folder = dir != null ? dir : ConfigFolder.standard(yard.shell().environment());
    PrintWriter out = spec.commandLine().getOut();
    PrintWriter err = spec.commandLine().getErr();
    if (mode.upgrade) {
      Upgrade.run(folder, out);
      return;
    }
    Optional<Reinit> reinit = mode.reinit ? Optional.of(Reinit.of(folder)) : Optional.empty();
    String name =
        workstation != null
            ? workstation
            : reinit
                .flatMap(old -> old.oldValue(Settings.WORKSTATION))
                .orElseGet(this::askWorkstation);
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
    // --reinit keeps the old values unless a flag replaces them, so it asks for neither.
    String domain =
        baseDomain != null ? baseDomain : !mode.reinit && atTerminal() ? askBaseDomain() : null;
    List<String> peerAddresses = peers != null ? peers : mode.reinit ? null : List.of();
    String key =
        gossipKey != null
            ? checkedGossipKey(gossipKey)
            : reinit
                .flatMap(old -> old.oldValue(Settings.GOSSIP_KEY))
                .orElseGet(Init::newGossipKey);

    // The old values first; what init finds fills only those left empty; then the flags.
    EnvFile env = Templates.env();
    reinit.ifPresent(old -> old.carryOver(env));
    fillIfEmpty(env, Settings.COMPOSE_PROFILES, Optional.of("dashboard"));
    fillIfEmpty(env, Settings.UID, yard.shell().id("-u"));
    fillIfEmpty(env, Settings.GID, yard.shell().id("-g"));
    fillIfEmpty(env, Settings.DOCKER_GID, dockerGid());
    env.set(Settings.WORKSTATION, name).set(Settings.GOSSIP_KEY, key);
    if (peerAddresses != null) {
      env.set(Settings.PEERS, String.join(" ", peerAddresses));
    }
    if (domain != null) {
      env.set(Settings.BASE_DOMAIN, domain);
    }

    Map<String, String> files = new LinkedHashMap<>();
    files.put(Templates.COMPOSE, Templates.compose());
    files.put(Templates.ENV, env.text());
    reinit.ifPresent(old ->
        old.backUp()
            .forEach((file, backup) -> out.println("Backed up " + file + " to " + backup + ".")));
    write(folder, files);

    if (isEmpty(env, Settings.DOCKER_GID)) {
      err.println(
          "Warning: no Docker socket at "
              + dockerSocket
              + ", so YARD_DOCKER_GID in .env is empty. Set it to what stat -c %g"
              + " /var/run/docker.sock prints, or the dashboard will not start.");
      err.flush();
    }
    out.println("Wrote " + String.join(", ", files.keySet()) + " to " + folder + ".");
    out.println();
    out.println("The Yard's gossip key: " + key);
    out.println("Give this key to every other Workstation: lvh-yard init --gossip-key <key>.");
    out.println();
    out.print(nextSteps(folder, env));
    out.println();
    out.println("To join a Yard later, run this with the address of one of its Workstations:");
    out.println("  lvh-yard init --reinit --peers <ip> --gossip-key <key>");
    out.flush();
  }

  private static void fillIfEmpty(EnvFile env, String key, Optional<String> found) {
    if (isEmpty(env, key)) {
      found.ifPresent(value -> env.set(key, value));
    }
  }

  /** A commented setting counts as empty. */
  private static boolean isEmpty(EnvFile env, String key) {
    return env.get(key).filter(value -> !value.isBlank()).isEmpty();
  }

  private static String nextSteps(Path folder, EnvFile env) {
    return """
    Next steps:
      1. Open TCP port %s and TCP and UDP port %s to the other Workstations.
      2. Start this Workstation: cd %s && docker compose up -d
      3. Open the dashboard: %s
      4. Make sure that LVH Yard works: lvh-yard status
    """
        .formatted(
            env.valueOrDefault(Settings.CONSUL_SERVER_PORT),
            env.valueOrDefault(Settings.CONSUL_SERF_WAN_PORT),
            folder,
            env.dashboardUrl());
  }

  /** Asks again until the name is valid. Enter takes the hostname, made into a name. */
  private String askWorkstation() {
    PrintWriter out = spec.commandLine().getOut();
    Optional<String> offered = nameFromHostname(hostname != null ? hostname : hostname());
    while (true) {
      String typed =
          ask(
              "Workstation name, unique in the Yard (lowercase letters, digits and -)"
                  + offered.map(name -> " [" + name + "]").orElse("")
                  + ": ",
              "--workstation");
      String name = typed.isEmpty() ? offered.orElse("") : typed;
      if (WORKSTATION.matcher(name).matches()) {
        return name;
      }
      out.println(
          (name.isEmpty() ? "A Workstation needs a name" : name + " is not a Workstation name")
              + ": use lowercase letters, digits and -, at most 63, not starting or ending with"
              + " -.");
    }
  }

  /** Asks again until the domain is valid. Enter takes the default. */
  private String askBaseDomain() {
    PrintWriter out = spec.commandLine().getOut();
    String standard = Templates.env().get(Settings.BASE_DOMAIN).orElseThrow();
    while (true) {
      String typed = ask("Base domain [" + standard + "]: ", "--base-domain");
      String domain = typed.isEmpty() ? standard : typed;
      if (DOMAIN.matcher(domain).matches()) {
        return domain;
      }
      out.println(domain + " is not a domain name such as lvh.me.");
    }
  }

  /**
   * The part of {@code hostname} before the first dot, as a Workstation name: lowercase, each
   * invalid character made {@code -}, no {@code -} at the ends, at most 63 characters.
   */
  static Optional<String> nameFromHostname(String hostname) {
    if (hostname == null) {
      return Optional.empty();
    }
    String name =
        hostname.strip().split("\\.", -1)[0].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
    name = name.replaceAll("^-+", "");
    name = name.substring(0, Math.min(name.length(), 63)).replaceAll("-+$", "");
    return WORKSTATION.matcher(name).matches() ? Optional.of(name) : Optional.empty();
  }

  /** The kernel's name for this computer, which needs no DNS lookup, else Java's. */
  private static String hostname() {
    try {
      return Files.readString(Path.of("/proc/sys/kernel/hostname"));
    } catch (IOException notLinux) {
      try {
        return InetAddress.getLocalHost().getHostName();
      } catch (IOException unknown) {
        return null;
      }
    }
  }

  /**
   * Without a terminal, or once the input ends, a usage error that names {@code flag}, because no
   * one can answer.
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
        answers =
            new BufferedReader(new InputStreamReader(yard.shell().input(), StandardCharsets.UTF_8));
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

  /** A terminal, or a test that types for one. */
  private boolean atTerminal() {
    return terminal || yard.shell().terminal();
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

  private Optional<String> dockerGid() {
    try {
      return Optional.of(String.valueOf(Files.getAttribute(dockerSocket, "unix:gid")));
    } catch (IOException | UnsupportedOperationException noSocket) {
      return Optional.empty();
    }
  }

  /**
   * Writes none of them when any exists, unless {@code --force}. {@code .env} is readable by its
   * owner only, because it holds the gossip key.
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
      // Before Compose starts, or Docker creates it, owned by root.
      Files.createDirectories(folder.resolve("backups"));
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
