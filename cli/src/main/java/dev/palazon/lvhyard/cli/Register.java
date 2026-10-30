package dev.palazon.lvhyard.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** {@code yard register}: a Static Instance of one of this Workstation's Services. */
@Command(name = "register", mixinStandardHelpOptions = true,
        description = "Registers a Static Instance of a Service on this Workstation.")
class Register implements Runnable {

    @ParentCommand
    Yard yard;

    @Parameters(paramLabel = "<service>", description = "The Service's name, a single DNS label.")
    String service;

    @Option(names = "--host", paramLabel = "<host>",
            description = "The address the Instance listens on (default: this Workstation's LAN address).")
    String host;

    @Option(names = "--port", paramLabel = "<port>", required = true,
            description = "The port the Instance listens on.")
    int port;

    @Option(names = "--check", paramLabel = "<type>",
            description = "How its Health is checked: tcp connects to the port, http GETs the path (default: http"
                    + " with --path, else tcp).")
    String check;

    @Option(names = "--path", paramLabel = "<path>",
            description = "The path an HTTP check GETs (default: /).")
    String path;

    @Option(names = "--interval", paramLabel = "<duration>",
            description = "How often the check runs, at least once a second, such as 5s or 1m (default: 10s).")
    String interval;

    @Option(names = "--timeout", paramLabel = "<duration>",
            description = "How long the check waits for an answer, such as 500ms (default: 2s).")
    String timeout;

    @Option(names = "--tag", paramLabel = "<tag>",
            description = "A word that says what the Instance is for. Repeat it for more tags.")
    List<String> tags = new ArrayList<>();

    @Option(names = "--meta", paramLabel = "<key=value>",
            description = "A note about the Instance. Repeat it for more notes.")
    Map<String, String> meta = new LinkedHashMap<>();

    @Spec
    CommandSpec spec;

    @Override
    public void run() {
        Dashboard.Instance instance = yard.dashboard().register(service, host, port,
                new Dashboard.Check(check, path, interval, timeout), tags, meta);
        spec.commandLine().getOut().println("Registered " + service + " at " + instance.address() + ", Instance "
                + instance.id() + ".");
        spec.commandLine().getOut().flush();
    }
}
