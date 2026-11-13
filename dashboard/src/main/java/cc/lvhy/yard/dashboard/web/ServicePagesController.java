package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.TraefikSettings;
import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.profiles.RouteProfiles;
import cc.lvhy.yard.dashboard.reachability.ReachabilityTester;
import cc.lvhy.yard.dashboard.services.Alias;
import cc.lvhy.yard.dashboard.services.Aliases;
import cc.lvhy.yard.dashboard.services.Service;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

/** The Service detail and Reachability pages. */
@Controller
class ServicePagesController {

  private final Workstation workstation;

  private final ServicePageAccess access;

  private final Aliases aliases;

  private final ReachabilityTester reachability;

  private final RouteProfiles routeProfiles;

  private final TraefikSettings traefik;

  ServicePagesController(
      Workstation workstation,
      ServicePageAccess access,
      Aliases aliases,
      ReachabilityTester reachability,
      RouteProfiles routeProfiles,
      TraefikSettings traefik) {
    this.workstation = workstation;
    this.access = access;
    this.aliases = aliases;
    this.reachability = reachability;
    this.routeProfiles = routeProfiles;
    this.traefik = traefik;
  }

  @GetMapping(path = "/services/{workstation}/{service}", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String service(@PathVariable("workstation") String home, @PathVariable String service) {
    Service found = access.anyService(home, service);
    return ServicePage.render(workstation, found, aliasesOf(found), routeProfiles.names(), traefik);
  }

  /** Only this Workstation reads the Aliases of its own Services. */
  private List<Alias> aliasesOf(Service service) {
    return workstation.is(service.workstation()) ? aliases.of(service.name()) : List.of();
  }

  /** The results are not kept. */
  @GetMapping(
      path = "/services/{workstation}/{service}/reachability",
      produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String reachability(@PathVariable("workstation") String home, @PathVariable String service) {
    Service found = access.anyService(home, service);
    return ReachabilityPage.render(workstation, found, reachability.test(found));
  }
}
