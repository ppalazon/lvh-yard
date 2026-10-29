package dev.palazon.lvhyard.cli;

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

    @Spec
    CommandSpec spec;

    @Override
    public void run() {
        Dashboard.Instance instance = yard.dashboard().register(service, host, port);
        spec.commandLine().getOut().println("Registered " + service + " at " + instance.address() + ", Instance "
                + instance.id() + ".");
        spec.commandLine().getOut().flush();
    }
}
