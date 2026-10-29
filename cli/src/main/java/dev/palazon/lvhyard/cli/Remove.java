package dev.palazon.lvhyard.cli;

import java.util.List;
import java.util.stream.Collectors;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** {@code yard remove}: a Static Instance of one of this Workstation's Services, by its ID or by where it is. */
@Command(name = "remove", mixinStandardHelpOptions = true,
        description = "Removes a Static Instance of a Service on this Workstation, by its ID or by its port.")
class Remove implements Runnable {

    @ParentCommand
    Yard yard;

    @Parameters(paramLabel = "<service>", description = "The Service's name.")
    String service;

    @ArgGroup(multiplicity = "1")
    Which which;

    @Spec
    CommandSpec spec;

    /** Either an ID, as {@code yard list} shows it, or a port with an optional host. */
    static class Which {

        @Option(names = "--id", paramLabel = "<id>", required = true, description = "The Instance's ID.")
        String id;

        @ArgGroup(exclusive = false, multiplicity = "1")
        Address address;
    }

    static class Address {

        @Option(names = "--port", paramLabel = "<port>", required = true,
                description = "The port the Instance listens on.")
        int port;

        @Option(names = "--host", paramLabel = "<host>",
                description = "The address it listens on; needed only when the port is used on more than one.")
        String host;
    }

    @Override
    public void run() {
        Dashboard dashboard = yard.dashboard();
        String removed;
        if (which.id != null) {
            dashboard.remove(service, which.id);
            removed = "Removed Instance " + which.id + " of " + service + ".";
        } else {
            Dashboard.Instance instance = instanceAt(dashboard.ownService(service));
            dashboard.remove(service, instance.id());
            removed = "Removed " + service + " at " + instance.address() + ".";
        }
        spec.commandLine().getOut().println(removed);
        spec.commandLine().getOut().flush();
    }

    /** The one Instance of {@code found} at the port, and at the host when one was given. */
    private Dashboard.Instance instanceAt(Dashboard.Service found) {
        Address address = which.address;
        List<Dashboard.Instance> matches = found.instances().stream()
                .filter(instance -> instance.port() == address.port
                        && (address.host == null || instance.host().equals(address.host)))
                .toList();
        if (matches.isEmpty()) {
            throw new YardException(service + " has no Instance at "
                    + (address.host == null ? "port " + address.port : Dashboard.address(address.host, address.port))
                    + ".");
        }
        if (matches.size() > 1) {
            throw new YardException(service + " has Instances at port " + address.port + " on more than one"
                    + " host (" + matches.stream().map(Dashboard.Instance::host).collect(Collectors.joining(", "))
                    + "): give --host.");
        }
        return matches.getFirst();
    }
}
