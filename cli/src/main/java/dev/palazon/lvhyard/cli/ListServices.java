package dev.palazon.lvhyard.cli;

import java.io.PrintWriter;
import java.util.List;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * {@code yard list}: Services grouped by Home Workstation, this one first, each with its state, its hostname, its
 * Instances and their Health.
 */
@Command(name = "list", mixinStandardHelpOptions = true,
        description = "Lists every Service in the Yard by Workstation, this one first, or one Workstation's, with"
                + " their state, hostname, Instances and Health.")
class ListServices implements Runnable {

    @ParentCommand
    Yard yard;

    @Option(names = "--workstation", paramLabel = "<name>",
            description = "The only Workstation whose Services to list (default: every one).")
    String workstation;

    @Spec
    CommandSpec spec;

    @Override
    public void run() {
        Dashboard dashboard = yard.dashboard();
        PrintWriter out = spec.commandLine().getOut();
        if (workstation == null) {
            Dashboard.Yard yard = dashboard.yard();
            print(out, yard.home().name() + " (this Workstation)", yard.home().services());
            for (Dashboard.YardWorkstation remote : yard.remotes()) {
                if (remote.unreachable()) {
                    out.println(remote.name() + ": Workstation unreachable");
                } else {
                    print(out, remote.name(), remote.services());
                }
            }
        } else {
            print(out, workstation, dashboard.services(workstation));
        }
        out.flush();
    }

    /** A heading for the Workstation called {@code name}, then its Services, or that it has none. */
    private static void print(PrintWriter out, String name, List<Dashboard.Service> services) {
        if (services.isEmpty()) {
            out.println(name + ": no Services");
            return;
        }
        out.println(name);
        for (Dashboard.Service service : services) {
            out.println("  " + service.name() + ": " + service.state()
                    + (service.hostname() == null ? "" : " at " + service.hostname()));
            for (Dashboard.Instance instance : service.instances()) {
                out.printf("    %-13s  %-8s  %s%s%n", instance.address(), instance.health(), instance.id(),
                        instance.outOfRange()
                                ? "  out of range: only " + service.workstation() + " can reach it, no Route"
                                : "");
            }
        }
    }
}
