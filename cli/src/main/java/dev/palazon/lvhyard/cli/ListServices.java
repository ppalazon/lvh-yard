package dev.palazon.lvhyard.cli;

import java.io.PrintWriter;
import java.util.List;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** {@code yard list}: Services, each with its state, its hostname, its Instances and their Health. */
@Command(name = "list", mixinStandardHelpOptions = true,
        description = "Lists this Workstation's Services, or another Workstation's, with their state, hostname,"
                + " Instances and Health.")
class ListServices implements Runnable {

    @ParentCommand
    Yard yard;

    @Option(names = "--workstation", paramLabel = "<name>",
            description = "The Workstation whose Services to list (default: this one).")
    String workstation;

    @Spec
    CommandSpec spec;

    @Override
    public void run() {
        Dashboard dashboard = yard.dashboard();
        print(workstation == null ? dashboard.ownServices() : dashboard.services(workstation));
    }

    private void print(List<Dashboard.Service> services) {
        PrintWriter out = spec.commandLine().getOut();
        if (services.isEmpty()) {
            out.println((workstation == null ? "This Workstation" : workstation) + " has no Services.");
        }
        for (Dashboard.Service service : services) {
            out.println(service.name() + " on " + service.workstation() + ": " + service.state()
                    + (service.hostname() == null ? "" : " at " + service.hostname()));
            for (Dashboard.Instance instance : service.instances()) {
                out.printf("  %-13s  %-8s  %s%n", instance.address(), instance.health(), instance.id());
            }
        }
        out.flush();
    }
}
