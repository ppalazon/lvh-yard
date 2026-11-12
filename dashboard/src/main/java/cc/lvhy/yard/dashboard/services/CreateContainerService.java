package cc.lvhy.yard.dashboard.services;

import cc.lvhy.yard.dashboard.Workstation;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Creates a new Service from one Container Binding, keeping progress if a write fails. */
@Component
public class CreateContainerService {

  private final Workstation workstation;

  private final Services services;

  private final ServiceRecords records;

  private final ContainerBindings containerBindings;

  private final Publications publications;

  public CreateContainerService(
      Workstation workstation,
      Services services,
      ServiceRecords records,
      ContainerBindings containerBindings,
      Publications publications) {
    this.workstation = workstation;
    this.services = services;
    this.records = records;
    this.containerBindings = containerBindings;
    this.publications = publications;
  }

  /** Validates every input before writing. Failed writes are not undone or retried. */
  public Result create(String name, ContainerBindingRequest binding, boolean publish, Scope scope) {
    String service = name == null ? "" : name.strip();
    Registrations.checkName(service);
    if (records.serviceExists(service)) {
      throw existing(service);
    }
    if (binding == null) {
      throw new InvalidRegistrationException("containerPort", "Choose a container to follow.");
    }
    if (scope == null) {
      throw new InvalidRegistrationException("scope", "Choose Local or Yard Scope.");
    }
    ContainerBindingRequest checked;
    try {
      checked = ContainerBindings.check(service, binding);
    } catch (InvalidRegistrationException invalid) {
      throw new InvalidRegistrationException("containerPort", invalid.getMessage());
    }

    String home = workstation.name();
    boolean created;
    try {
      created = services.create(home, service);
    } catch (RuntimeException failure) {
      return new Result(service, false, List.of(), failureMessage(failure));
    }
    if (!created) {
      throw existing(service);
    }

    List<String> completed = new ArrayList<>(3);
    completed.add("Created Service " + service);
    try {
      containerBindings.add(home, service, checked);
      String target =
          checked.followsCompose()
              ? checked.composeProject() + "/" + checked.composeService()
              : checked.container();
      completed.add("Bound " + target + " on Container Port " + checked.port());
      if (publish) {
        publications.publish(home, service, new PublicationRequest(scope, null, null));
        completed.add("Published with " + (scope == Scope.LOCAL ? "Local" : "Yard") + " Scope");
      }
      return new Result(service, true, completed, null);
    } catch (RuntimeException failure) {
      return new Result(service, true, completed, failureMessage(failure));
    }
  }

  private static InvalidRegistrationException existing(String service) {
    return new InvalidRegistrationException(
        "name", "Service " + service + " already exists. Choose a new name.");
  }

  private static String failureMessage(RuntimeException failure) {
    String message = failure.getMessage();
    return message == null || message.isBlank()
        ? "A write failed. Check the Service before trying again."
        : message;
  }

  public record Result(String name, boolean created, List<String> completed, String failure) {
    public Result {
      completed = List.copyOf(completed);
    }
  }
}
