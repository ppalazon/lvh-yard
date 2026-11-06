package dev.palazon.lvhyard.cli;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
    name = "containers",
    mixinStandardHelpOptions = true,
    description = "Lists the running containers that LVH Yard does not follow yet.")
class Containers implements Runnable {

  @ParentCommand Yard yard;

  @Spec CommandSpec spec;

  @Option(names = "--details", description = "Shows container names and Docker port mappings.")
  boolean details;

  @Override
  public void run() {
    List<Dashboard.UnfollowedContainer> containers = yard.dashboard().containers();
    PrintWriter out = spec.commandLine().getOut();
    if (containers.isEmpty()) {
      out.println("Every running container is followed.");
    }
    Map<String, Map<String, List<Dashboard.UnfollowedContainer>>> projects = new TreeMap<>();
    List<Dashboard.UnfollowedContainer> others = new ArrayList<>();
    for (Dashboard.UnfollowedContainer container : containers) {
      if (container.composeProject() != null && container.composeService() != null) {
        projects
            .computeIfAbsent(container.composeProject(), project -> new TreeMap<>())
            .computeIfAbsent(container.composeService(), service -> new ArrayList<>())
            .add(container);
      } else {
        others.add(container);
      }
    }
    projects.forEach((project, services) -> {
      out.println("Compose project " + project);
      services.forEach((service, replicas) -> printService(out, service, replicas));
    });
    if (!others.isEmpty()) {
      out.println("Other containers");
      others.sort(
          Comparator.comparing(Dashboard.UnfollowedContainer::service)
              .thenComparing(Dashboard.UnfollowedContainer::name));
      for (Dashboard.UnfollowedContainer container : others) {
        printService(out, container.service(), List.of(container));
      }
    }
    out.flush();
  }

  private void printService(
      PrintWriter out, String service, List<Dashboard.UnfollowedContainer> containers) {
    String ports =
        containers.stream()
            .flatMap(container -> container.ports().stream())
            .mapToInt(Dashboard.ContainerPort::containerPort)
            .distinct()
            .sorted()
            .mapToObj(Integer::toString)
            .collect(Collectors.joining(", "));
    String registration = containers.get(0).service();
    out.println(
        "  "
            + service
            + ": "
            + containers.size()
            + (containers.size() == 1 ? " container; " : " containers; ")
            + (ports.isEmpty() ? "no ports" : ports)
            + (service.equals(registration) ? "" : "; registers as " + registration));
    if (details) {
      containers.stream()
          .sorted(Comparator.comparing(Dashboard.UnfollowedContainer::name))
          .forEach(container ->
              out.println(
                  "    "
                      + container.name()
                      + ": "
                      + mappings(container.ports())
                      + (container.health() == null ? "" : "; " + container.health())));
    }
  }

  private static String mappings(List<Dashboard.ContainerPort> ports) {
    if (ports.isEmpty()) {
      return "no ports";
    }
    return ports.stream()
        .distinct()
        .sorted(
            Comparator.comparingInt(Dashboard.ContainerPort::containerPort)
                .thenComparing(
                    Dashboard.ContainerPort::hostIp,
                    Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(
                    Dashboard.ContainerPort::hostPort,
                    Comparator.nullsFirst(Comparator.naturalOrder())))
        .map(port -> {
          if (port.hostPort() == null) {
            return port.containerPort() + " not published";
          }
          String host = port.hostIp().contains(":") ? "[" + port.hostIp() + "]" : port.hostIp();
          return host + ":" + port.hostPort() + "->" + port.containerPort();
        })
        .collect(Collectors.joining(", "));
  }
}
