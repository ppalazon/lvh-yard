package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.ContainerBindingRequest;
import dev.palazon.lvhyard.dashboard.services.ContainerBindings;
import dev.palazon.lvhyard.dashboard.services.ContainerList;
import dev.palazon.lvhyard.dashboard.services.FollowedContainerException;
import dev.palazon.lvhyard.dashboard.services.InvalidRegistrationException;
import dev.palazon.lvhyard.dashboard.services.ListedContainer;
import dev.palazon.lvhyard.dashboard.services.Service;
import dev.palazon.lvhyard.dashboard.services.UnknownServiceException;
import dev.palazon.lvhyard.dashboard.services.UnreadableDockerException;
import dev.palazon.lvhyard.dashboard.services.YardReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/** The Service page's form that binds a container or a Compose service. */
@Controller
class BindContainerPagesController {

  /** The request fields that name the target. The form has one field for all of them. */
  private static final Set<String> TARGET_FIELDS =
      Set.of("container", "composeProject", "composeService");

  private final Workstation workstation;

  private final YardReader yard;

  private final ContainerBindings containerBindings;

  private final ContainerList containerList;

  BindContainerPagesController(
      Workstation workstation,
      YardReader yard,
      ContainerBindings containerBindings,
      ContainerList containerList) {
    this.workstation = workstation;
    this.yard = yard;
    this.containerBindings = containerBindings;
    this.containerList = containerList;
  }

  @GetMapping(
      path = "/services/{workstation}/{service}/container-bindings/new",
      produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String bindForm(@PathVariable("workstation") String home, @PathVariable String service) {
    return page(ownService(home, service), BindContainerPage.Bind.EMPTY, null);
  }

  /** A refused post answers the form again, with what was typed and the reason at its field. */
  @PostMapping(
      path = "/services/{workstation}/{service}/container-bindings",
      produces = MediaType.TEXT_HTML_VALUE)
  ResponseEntity<String> bind(
      @PathVariable("workstation") String home,
      @PathVariable String service,
      BindContainerPage.Bind bind) {
    Service found = ownService(home, service);
    InvalidRegistrationException error;
    try {
      containerBindings.add(home, service, bind.request());
      return ResponseEntity.status(HttpStatus.SEE_OTHER)
          .location(URI.create(ServicePage.path(found)))
          .build();
    } catch (InvalidRegistrationException e) {
      error = atTarget(e.field(), e.getMessage());
    } catch (FollowedContainerException e) {
      error = atTarget(e.field(), e.getMessage());
    } catch (UnknownServiceException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    }
    return ResponseEntity.badRequest().contentType(Layout.HTML).body(page(found, bind, error));
  }

  private static InvalidRegistrationException atTarget(String field, String message) {
    return new InvalidRegistrationException(
        TARGET_FIELDS.contains(field) ? BindContainerPage.TARGET : field, message);
  }

  private String page(
      Service service, BindContainerPage.Bind bind, InvalidRegistrationException e) {
    List<BindContainerPage.Target> targets;
    String dockerProblem = null;
    try {
      targets = targets(containerList.containers());
    } catch (UnreadableDockerException problem) {
      targets = List.of();
      dockerProblem = problem.getMessage();
    }
    return BindContainerPage.render(workstation, service, targets, dockerProblem, bind, e);
  }

  /**
   * Each Compose service once, with the Container Ports of all its containers, then each container
   * with no Compose project. The list comes with Compose projects first.
   */
  private List<BindContainerPage.Target> targets(List<ListedContainer> containers) {
    Map<String, List<ListedContainer>> compose = new LinkedHashMap<>();
    List<BindContainerPage.Target> plain = new ArrayList<>();
    for (ListedContainer container : containers) {
      if (container.composeProject() != null) {
        compose
            .computeIfAbsent(
                container.composeProject() + "/" + container.composeService(),
                target -> new ArrayList<>())
            .add(container);
      } else {
        plain.add(
            new BindContainerPage.Target(
                container.name(), false, container.containerPorts(), container.followedBy()));
      }
    }
    List<BindContainerPage.Target> targets = new ArrayList<>();
    compose.forEach((value, members) -> {
      ListedContainer first = members.getFirst();
      targets.add(
          new BindContainerPage.Target(
              value,
              true,
              members.stream()
                  .flatMap(member -> member.containerPorts().stream())
                  .distinct()
                  .sorted()
                  .toList(),
              containerList.following(
                  ContainerBindingRequest.compose(
                      first.composeProject(), first.composeService(), null))));
    });
    targets.addAll(plain);
    return targets;
  }

  /** Only this Workstation changes its own Services. */
  private Service ownService(String home, String service) {
    if (!workstation.is(home)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "The Services of " + home + " can be edited only on " + home + ".");
    }
    return yard.service(home, service)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }
}
