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

/**
 * {@code yard remove}: a Static Instance of one of this Workstation's Services, by its ID or by where it is, or a
 * Container Binding, by its container.
 */
@Command(name = "remove", mixinStandardHelpOptions = true,
        description = "Removes a Static Instance of a Service on this Workstation, by its ID or by its port, or a"
                + " Container Binding, by its container.")
class Remove implements Runnable {

    @ParentCommand
    Yard yard;

    @Parameters(paramLabel = "<service>", description = "The Service's name.")
    String service;

    @ArgGroup(multiplicity = "1")
    Which which;

    @Spec
    CommandSpec spec;

    /** Either an ID, as {@code yard list} shows it, or where it is: a port, a host or a container. */
    static class Which {

        @Option(names = "--id", paramLabel = "<id>", required = true, description = "The Instance's ID.")
        String id;

        @ArgGroup(exclusive = false, multiplicity = "1")
        Where where;
    }

    static class Where {

        @Option(names = "--port", paramLabel = "<port>",
                description = "The port the Instance listens on; with --container, the port inside the container,"
                        + " needed only when the container is bound on more than one.")
        Integer port;

        @Option(names = "--host", paramLabel = "<host>",
                description = "The address it listens on; needed only when the port is used on more than one.")
        String host;

        @Option(names = "--container", paramLabel = "<name>",
                description = "The container a Container Binding follows: removes the Binding and its Bound"
                        + " Instance.")
        String container;
    }

    @Override
    public void run() {
        Dashboard dashboard = yard.dashboard();
        String removed;
        if (which.id != null) {
            dashboard.remove(service, which.id);
            removed = "Removed Instance " + which.id + " of " + service + ".";
        } else if (which.where.container != null) {
            if (which.where.host != null) {
                throw new YardException("A Container Binding has no --host: give --container, and --port if it is"
                        + " bound on more than one.");
            }
            Dashboard.ContainerBinding binding = bindingTo(dashboard.ownService(service));
            dashboard.unbind(service, binding.id());
            removed = "Removed the Container Binding of " + service + " to container " + binding.container()
                    + ", port " + binding.port() + ".";
        } else if (which.where.port == null) {
            throw new YardException("Give --id, --port or --container: which Instance or Container Binding of "
                    + service + " to remove.");
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
        Where where = which.where;
        List<Dashboard.Instance> matches = found.instances().stream()
                .filter(instance -> instance.port() == where.port
                        && (where.host == null || instance.host().equals(where.host)))
                .toList();
        if (matches.isEmpty()) {
            throw new YardException(service + " has no Instance at "
                    + (where.host == null ? "port " + where.port : Dashboard.address(where.host, where.port))
                    + ".");
        }
        if (matches.size() > 1) {
            throw new YardException(service + " has Instances at port " + where.port + " on more than one"
                    + " host (" + matches.stream().map(Dashboard.Instance::host).collect(Collectors.joining(", "))
                    + "): give --host.");
        }
        return matches.getFirst();
    }

    /** The one Container Binding of {@code found} to the container, and to the port when one was given. */
    private Dashboard.ContainerBinding bindingTo(Dashboard.Service found) {
        Where where = which.where;
        List<Dashboard.ContainerBinding> matches = found.containerBindings().stream()
                .filter(binding -> binding.container().equals(where.container)
                        && (where.port == null || binding.port() == where.port))
                .toList();
        if (matches.isEmpty()) {
            throw new YardException(service + " has no Container Binding to container " + where.container
                    + (where.port == null ? "" : ", port " + where.port) + ".");
        }
        if (matches.size() > 1) {
            throw new YardException(service + " is bound to container " + where.container + " on more than one"
                    + " port (" + matches.stream().map(binding -> String.valueOf(binding.port()))
                            .collect(Collectors.joining(", ")) + "): give --port.");
        }
        return matches.getFirst();
    }
}
