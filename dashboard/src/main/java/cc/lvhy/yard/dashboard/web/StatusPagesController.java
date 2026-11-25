package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.status.StatusReader;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
class StatusPagesController {

  private final Workstation workstation;

  private final StatusReader status;

  /** The version of the dashboard, or {@code unknown} when the build left no build information. */
  private final String version;

  @Autowired
  StatusPagesController(
      Workstation workstation, StatusReader status, ObjectProvider<BuildProperties> build) {
    this(workstation, status, versionOf(build.getIfAvailable()));
  }

  private StatusPagesController(Workstation workstation, StatusReader status, String version) {
    this.workstation = workstation;
    this.status = status;
    this.version = version;
  }

  private static String versionOf(BuildProperties properties) {
    return properties == null ? "unknown" : properties.getVersion();
  }

  @GetMapping(path = StatusPage.PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String status() {
    return StatusPage.render(workstation, status.status(), version);
  }

  @GetMapping(path = StatusSummary.PATH, produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  String summary() {
    return StatusSummary.render(status.status());
  }
}
