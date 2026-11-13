package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.services.Service;
import cc.lvhy.yard.dashboard.services.UnknownWorkstationException;
import cc.lvhy.yard.dashboard.services.UnreachableWorkstationException;
import cc.lvhy.yard.dashboard.services.YardReader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Service lookup and edit access for the HTML pages. */
@Component
class ServicePageAccess {

  private final Workstation workstation;

  private final YardReader yard;

  ServicePageAccess(Workstation workstation, YardReader yard) {
    this.workstation = workstation;
    this.yard = yard;
  }

  Service anyService(String home, String service) {
    try {
      return yard.service(home, service)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    } catch (UnknownWorkstationException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
    } catch (UnreachableWorkstationException e) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), e);
    }
  }

  /** A Remote Service's Instances cannot be edited, so it is forbidden. */
  Service ownService(String home, String service) {
    if (!workstation.is(home)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "The Services of " + home + " can be edited only on " + home + ".");
    }
    return yard.service(home, service)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }
}
