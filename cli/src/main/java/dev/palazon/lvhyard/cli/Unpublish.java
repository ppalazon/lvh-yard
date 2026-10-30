package dev.palazon.lvhyard.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** {@code yard unpublish}: takes away one of this Workstation's Services' hostname, and keeps its Instances. */
@Command(name = "unpublish", mixinStandardHelpOptions = true,
        description = "Unpublishes a Service of this Workstation. Its Instances stay registered.")
class Unpublish implements Runnable {

    @ParentCommand
    Yard yard;

    @Parameters(paramLabel = "<service>", description = "The Service's name.")
    String service;

    @Spec
    CommandSpec spec;

    @Override
    public void run() {
        yard.dashboard().unpublish(service);
        spec.commandLine().getOut().println("Unpublished " + service + ". Its Instances stay registered.");
        spec.commandLine().getOut().flush();
    }
}
