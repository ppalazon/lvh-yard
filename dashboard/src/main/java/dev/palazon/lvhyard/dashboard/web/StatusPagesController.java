package dev.palazon.lvhyard.dashboard.web;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.status.StatusReader;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** The status page of LVH Yard on this Workstation. */
@Controller
class StatusPagesController {

  private final Workstation workstation;

  private final StatusReader status;

  StatusPagesController(Workstation workstation, StatusReader status) {
    this.workstation = workstation;
    this.status = status;
  }

  @GetMapping(path = StatusPage.PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String status() {
    return StatusPage.render(workstation, status.status());
  }
}
