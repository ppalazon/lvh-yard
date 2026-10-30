package dev.palazon.lvhyard.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** {@code yard publish}: gives one of this Workstation's Services its hostname, with Local Scope. */
@Command(name = "publish", mixinStandardHelpOptions = true,
        description = "Publishes a Service of this Workstation at <service>.<base-domain>, with Local Scope.")
class Publish implements Runnable {

    @ParentCommand
    Yard yard;

    @Parameters(paramLabel = "<service>", description = "The Service's name.")
    String service;

    @Spec
    CommandSpec spec;

    @Override
    public void run() {
        Dashboard.Service published = yard.dashboard().publish(service);
        String scope = published.publication().scope();
        spec.commandLine().getOut().println("Published " + service + " at " + published.hostname() + ", with "
                + Character.toUpperCase(scope.charAt(0)) + scope.substring(1) + " Scope. It is "
                + published.state() + ".");
        spec.commandLine().getOut().flush();
    }
}
