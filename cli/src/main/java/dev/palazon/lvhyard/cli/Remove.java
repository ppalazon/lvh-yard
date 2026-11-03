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

@Command(
    name = "remove",
    mixinStandardHelpOptions = true,
    description = "Removes an Instance or a Container Binding.")
class Remove implements Runnable {

  @ParentCommand Yard yard;

  @Parameters(paramLabel = "<service>", description = "The Service's name.")
  String service;

  @ArgGroup(multiplicity = "1")
  Which which;

  @Spec CommandSpec spec;

  static class Which {

    @Option(
        names = "--id",
        paramLabel = "<id>",
        required = true,
        description = "The Instance's ID.")
    String id;

    @ArgGroup(exclusive = false, multiplicity = "1")
    Where where;
  }

  static class Where {

    @Option(
        names = "--port",
        paramLabel = "<port>",
        description =
            "The Instance's port. With --container, the port inside the container, needed only when"
                + " it is bound on several ports.")
    Integer port;

    @Option(
        names = "--host",
        paramLabel = "<host>",
        description = "The Instance's address, needed only when several Instances share the port.")
    String host;

    @Option(
        names = "--container",
        paramLabel = "<name>",
        description = "Removes the Container Binding of this container, and its Bound Instance.")
    String container;

    @Option(
        names = "--compose-project",
        paramLabel = "<project>",
        description =
            "With --compose-service, removes the Container Binding of that Compose service, and its"
                + " Bound Instances.")
    String composeProject;

    @Option(
        names = "--compose-service",
        paramLabel = "<service>",
        description = "The Compose service, in --compose-project.")
    String composeService;

    boolean binding() {
      return container != null || composeProject != null || composeService != null;
    }
  }

  @Override
  public void run() {
    Dashboard dashboard = yard.dashboard();
    String removed;
    if (which.id != null) {
      dashboard.remove(service, which.id);
      removed = "Removed Instance " + which.id + " of " + service + ".";
    } else if (which.where.binding()) {
      Where where = which.where;
      if (where.host != null) {
        throw new YardException(
            "A Container Binding has no --host. Give --container, or --compose-project and"
                + " --compose-service.");
      }
      if (where.container != null
          && (where.composeProject != null || where.composeService != null)) {
        throw new YardException(
            "Give --container, or --compose-project and --compose-service, not both.");
      }
      if (where.container == null
          && (where.composeProject == null || where.composeService == null)) {
        throw new YardException(
            "A Compose service needs both --compose-project and --compose-service.");
      }
      Dashboard.ContainerBinding binding = bindingTo(dashboard.ownService(service));
      dashboard.unbind(service, binding.id());
      removed =
          "Removed the Container Binding of "
              + service
              + " to "
              + binding.follows()
              + ", port "
              + binding.port()
              + ".";
    } else if (which.where.port == null) {
      throw new YardException(
          "Say what to remove from "
              + service
              + ": give --id, --port, --container, or --compose-project and --compose-service.");
    } else {
      Dashboard.Instance instance = instanceAt(dashboard.ownService(service));
      dashboard.remove(service, instance.id());
      removed = "Removed " + service + " at " + instance.address() + ".";
    }
    spec.commandLine().getOut().println(removed);
    spec.commandLine().getOut().flush();
  }

  private Dashboard.Instance instanceAt(Dashboard.Service found) {
    Where where = which.where;
    List<Dashboard.Instance> matches =
        found.instances().stream()
            .filter(instance ->
                instance.port() == where.port
                    && (where.host == null || instance.host().equals(where.host)))
            .toList();
    if (matches.isEmpty()) {
      throw new YardException(
          service
              + " has no Instance at "
              + (where.host == null
                  ? "port " + where.port
                  : Dashboard.address(where.host, where.port))
              + ".");
    }
    if (matches.size() > 1) {
      throw new YardException(
          service
              + " has Instances at port "
              + where.port
              + " on more than one"
              + " host ("
              + matches.stream().map(Dashboard.Instance::host).collect(Collectors.joining(", "))
              + "): give --host.");
    }
    return matches.getFirst();
  }

  private Dashboard.ContainerBinding bindingTo(Dashboard.Service found) {
    Where where = which.where;
    List<Dashboard.ContainerBinding> matches =
        found.containerBindings().stream()
            .filter(binding ->
                (where.container != null
                        ? where.container.equals(binding.container())
                        : where.composeProject.equals(binding.composeProject())
                            && where.composeService.equals(binding.composeService()))
                    && (where.port == null || binding.port() == where.port))
            .toList();
    String follows =
        where.container != null
            ? "container " + where.container
            : "Compose service " + where.composeService + " of project " + where.composeProject;
    if (matches.isEmpty()) {
      throw new YardException(
          service
              + " has no Container Binding to "
              + follows
              + (where.port == null ? "" : ", port " + where.port)
              + ".");
    }
    if (matches.size() > 1) {
      throw new YardException(
          service
              + " is bound to "
              + follows
              + " on more than one"
              + " port ("
              + matches.stream()
                  .map(binding -> String.valueOf(binding.port()))
                  .collect(Collectors.joining(", "))
              + "): give --port.");
    }
    return matches.getFirst();
  }
}
