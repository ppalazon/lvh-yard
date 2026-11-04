package dev.palazon.lvhyard.cli;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
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
      Containers.class,
      Import.class,
      Remove.class,
      Publish.class,
      Unpublish.class,
      Alias.class,
      TestReachability.class,
      Status.class
    },
    description = "Registers, publishes and tests Services on this Workstation.")
public class Yard implements Runnable {

  /** Where the dashboard listens when no .env gives its address. */
  private static final String LOOPBACK_DASHBOARD = "http://127.0.0.1:7780";

  @Option(
      names = "--dashboard",
      paramLabel = "<url>",
      description = {
        "The dashboard on this Workstation.",
        "Default: YARD_DASHBOARD_URL, else the address",
        "in the .env from init (in YARD_CONFIG_DIR,",
        "$XDG_CONFIG_HOME/lvh-yard or ~/.config/lvh-yard),",
        "else " + LOOPBACK_DASHBOARD + "."
      })
  URI dashboard;

  @Spec CommandSpec spec;

  @Override
  public void run() {
    spec.commandLine().usage(spec.commandLine().getOut());
  }

  Dashboard dashboard() {
    return new Dashboard(dashboard != null ? dashboard : defaultDashboard(System.getenv()));
  }

  /**
   * The dashboard's address when {@code --dashboard} does not name one: {@code YARD_DASHBOARD_URL},
   * else the {@code .env} in the config folder, else {@link #LOOPBACK_DASHBOARD}.
   */
  private static URI defaultDashboard(Map<String, String> environment) {
    String url = environment.get(Settings.DASHBOARD_URL);
    if (url != null && !url.isBlank()) {
      return URI.create(url);
    }
    try {
      Path env = ConfigFolder.forReading(environment).resolve(Templates.ENV);
      return EnvFile.parse(Files.readString(env)).dashboardUrl();
    } catch (IOException missingOrUnreadable) {
      return URI.create(LOOPBACK_DASHBOARD);
    }
  }

  /** A failure to talk to the dashboard is printed, and exits with 1. */
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

  private static void withVersion(CommandLine command) {
    command.getCommandSpec().version("lvh-yard " + Version.VERSION);
    command.getSubcommands().values().forEach(Yard::withVersion);
  }

  public static void main(String[] args) {
    System.exit(commandLine().execute(args));
  }
}
