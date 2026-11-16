package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The stylesheet that every page loads: the Phyxor theme with the system fonts. */
class StylesheetComponentTest extends DashboardComponentTest {

  @Test
  void usesOnlyTheSystemFonts() {
    String css = stylesheet();

    assertThat(declared(css, "--font-sans"))
        .isEqualToIgnoringWhitespace("ui-sans-serif, system-ui, sans-serif");
    assertThat(declared(css, "--font-mono")).isEqualToIgnoringWhitespace("ui-monospace, monospace");
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

  /**
   * The last value that {@code css} gives the custom property {@code name}, as the browser takes
   * it.
   */
  private static String declared(String css, String name) {
    Matcher declaration =
        Pattern.compile("(?:^|[;{])" + Pattern.quote(name) + ":([^;}]*)").matcher(css);
    String value = null;
    while (declaration.find()) {
      value = declaration.group(1).trim();
    }
    assertThat(value).as(name).isNotNull();
    return value;
  }
}
