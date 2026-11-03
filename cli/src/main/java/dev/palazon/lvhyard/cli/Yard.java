package dev.palazon.lvhyard.cli;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Map;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * The {@code lvh-yard} command. It talks only to the dashboard on its own Workstation, through the
 * REST API that {@code openapi.yaml} describes; it never talks to Consul. {@code init} talks to
 * neither: it writes the Workstation's files.
 */
@Command(
    name = "lvh-yard",
    mixinStandardHelpOptions = true,
    subcommands = {
      Init.class,
      ListServices.class,
      Register.class,
      Remove.class,
      Publish.class,
      Unpublish.class,
      Alias.class,
      TestReachability.class
    },
    description = "Registers, publishes and tests Services on this Workstation's LVH Yard.")
public class Yard implements Runnable {

  /** Where {@code mise run dashboard} serves the dashboard. */
  private static final String LOOPBACK_DASHBOARD = "http://127.0.0.1:7780";

  @Option(
      names = "--dashboard",
      paramLabel = "<url>",
      description = {
        "The dashboard on this Workstation.",
        "Default: YARD_DASHBOARD_URL when set,",
        "else the bind address and port in the .env",
        "that init wrote, in YARD_CONFIG_DIR, else",
        "$XDG_CONFIG_HOME/lvh-yard or ~/.config/lvh-yard,",
        "else " + LOOPBACK_DASHBOARD + "."
      })
  URI dashboard;

  @Spec CommandSpec spec;

  /** With no subcommand there is nothing to do but show what the command offers. */
  @Override
  public void run() {
    spec.commandLine().usage(spec.commandLine().getOut());
  }

  /** The dashboard that {@code --dashboard} names, else the first one the defaults find. */
  Dashboard dashboard() {
    return new Dashboard(dashboard != null ? dashboard : defaultDashboard(System.getenv()));
  }

  /**
   * The dashboard's address when {@code --dashboard} does not name one: {@code YARD_DASHBOARD_URL},
   * else the {@code .env} in the config folder, else {@link #LOOPBACK_DASHBOARD}.
   */
  private static URI defaultDashboard(Map<String, String> environment) {
    String url = environment.get("YARD_DASHBOARD_URL");
    if (url != null && !url.isBlank()) {
      return URI.create(url);
    }
    EnvFile env;
    try {
      env = EnvFile.parse(Files.readString(ConfigFolder.forReading(environment).resolve(".env")));
    } catch (IOException missingOrUnreadable) {
      return URI.create(LOOPBACK_DASHBOARD);
    }
    // The wildcard listens on loopback too; the dashboard's own BindAddress reaches it there.
    String address =
        env.get("YARD_DASHBOARD_BIND_ADDRESS")
            .filter(bind -> !bind.isBlank() && !bind.equals("0.0.0.0"))
            .orElse("127.0.0.1");
    String port = env.get("YARD_DASHBOARD_PORT").filter(set -> !set.isBlank()).orElse("7780");
    return URI.create("http://" + address + ":" + port);
  }

  /**
   * The command line, ready to execute: a failure to talk to the dashboard is printed and exits
   * with 1.
   */
  static CommandLine commandLine() {
    CommandLine yard =
        new CommandLine(new Yard())
            .setExecutionExceptionHandler((exception, command, parseResult) -> {
              if (!(exception instanceof YardException)) {
                throw exception;
              }
              command.getErr().println(exception.getMessage());
              command.getErr().flush();
              return 1;
            });
    withVersion(yard);
    return yard;
  }

  /** {@code command} and each of its subcommands print this build's version for -V. */
  private static void withVersion(CommandLine command) {
    command.getCommandSpec().version("lvh-yard " + Version.VERSION);
    command.getSubcommands().values().forEach(Yard::withVersion);
  }

  public static void main(String[] args) {
    System.exit(commandLine().execute(args));
  }
}
