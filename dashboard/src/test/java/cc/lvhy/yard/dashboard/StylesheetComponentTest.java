package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The stylesheet that every page loads: the Phyxor theme with the system fonts. */
class StylesheetComponentTest extends DashboardComponentTest {

  @Test
  void usesOnlyTheSystemFonts() {
    String css = stylesheet();

    assertThat(css)
        .doesNotContain("@font-face")
        .doesNotContainPattern("@import")
        .doesNotContainPattern("(?i)inter[,\"']|jetbrains|source serif|geist|fonts\\.googleapis");
  }

  @Test
  void hasTheSuccessBadgeAndTheRiskAlert() {
    assertThat(stylesheet())
        .containsPattern("\\.badge\\[data-variant=\"?success\"?\\]")
        .containsPattern("\\.alert\\[data-variant=\"?risk\"?\\]");
  }

  private String stylesheet() {
    return getPage("/app.css").getBody();
  }
}
