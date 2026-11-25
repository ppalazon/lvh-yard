package cc.lvhy.yard.dashboard.web;

import static org.assertj.core.api.Assertions.assertThat;

import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.status.Part;
import cc.lvhy.yard.dashboard.status.PartState;
import cc.lvhy.yard.dashboard.status.Status;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What the Status page says about the version of the dashboard. */
class StatusPageTest {

  @Test
  void theVersionIsABadgeAfterTheHeadingAndTheOverallStatus() {
    String page =
        StatusPage.render(
            new Workstation("ws-a", "lvh.me"),
            Status.of(
                List.of(new Part("Docker", PartState.OK, "Docker answers.", List.of(), List.of()))),
            "0.2.0");

    int version = page.indexOf("id=\"dashboard-version\"");
    assertThat(page.substring(version, page.indexOf("</a>", version)))
        .contains("class=\"badge tabular-nums\"")
        .contains("href=\"https://github.com/ppalazon/lvh-yard/releases\"")
        .contains("target=\"_blank\"")
        .contains("rel=\"noopener noreferrer\"")
        .contains("aria-label=\"Dashboard version 0.2.0. Releases on GitHub (opens in a new tab)\"")
        .contains("<svg")
        .endsWith("</svg>v0.2.0");
    assertThat(version)
        .isGreaterThan(page.indexOf("<h1>Status"))
        .isGreaterThan(page.indexOf("✓ OK"));
  }

  @Test
  void aVersionThatIsNotANumberKeepsItsText() {
    String page =
        StatusPage.render(
            new Workstation("ws-a", "lvh.me"),
            Status.of(
                List.of(new Part("Docker", PartState.OK, "Docker answers.", List.of(), List.of()))),
            "unknown");

    assertThat(page).contains("</svg>unknown</a>").doesNotContain("vunknown");
  }
}
