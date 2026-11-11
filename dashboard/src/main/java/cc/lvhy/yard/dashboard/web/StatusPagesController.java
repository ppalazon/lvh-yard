package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.status.StatusReader;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

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

  @GetMapping(path = StatusSummary.PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String summary() {
    return StatusSummary.render(status.status());
  }
}
