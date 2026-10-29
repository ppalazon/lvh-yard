package dev.palazon.lvhyard.cli;

import java.net.URI;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * The {@code yard} command. It talks only to the dashboard on its own Workstation, through the REST API that
 * {@code openapi.yaml} describes; it never talks to Consul.
 */
@Command(name = "yard", mixinStandardHelpOptions = true,
        description = "Registers, publishes and tests Services on this Workstation's LVH Yard.")
public class Yard implements Runnable {

    @Option(names = "--dashboard", paramLabel = "<url>",
            defaultValue = "${env:YARD_DASHBOARD_URL:-http://127.0.0.1:8780}",
            description = "The dashboard on this Workstation (default: ${DEFAULT-VALUE}, or YARD_DASHBOARD_URL).")
    URI dashboard;

    @Spec
    CommandSpec spec;

    /** With no subcommand there is nothing to do but show what the command offers. */
    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new Yard()).execute(args));
    }
}
