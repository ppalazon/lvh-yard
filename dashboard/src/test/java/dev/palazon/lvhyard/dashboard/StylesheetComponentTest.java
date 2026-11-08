package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The stylesheet that every page loads: the Phyxor theme with the system fonts. */
class StylesheetComponentTest extends DashboardComponentTest {

  /** The Phyxor primary amber, as its oklch hue. tweakcn theme cmq3zh8z1000104i6bwg93nqa. */
  private static final String PHYXOR_PRIMARY_HUE = "91.9268";

  @Test
  void lightThemeHasThePhyxorColors() {
    String light = rules(":root");

    assertThat(declared(light, "--primary")).contains(PHYXOR_PRIMARY_HUE);
    assertThat(declared(light, "--accent-foreground")).contains("46.2007");
    assertThat(declared(light, "--radius")).isEqualTo(".375rem");
  }

  @Test
  void darkThemeHasThePhyxorColors() {
    String dark = rules(".dark");

    assertThat(declared(dark, "--primary")).contains(PHYXOR_PRIMARY_HUE);
    // The Phyxor dark background, oklch(0.2046 0 0), however the build writes the lightness.
    assertThat(declared(dark, "--background"))
        .containsPattern("^oklch\\((0?\\.2046|20\\.46%) 0 0\\)$");
  }

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

  /** The declarations of every rule whose selector is exactly {@code selector}, joined. */
  private String rules(String selector) {
    Matcher rule =
        Pattern.compile("(?:^|[}{;])" + Pattern.quote(selector) + "\\{([^}]*)}")
            .matcher(stylesheet());
    StringBuilder declarations = new StringBuilder();
    while (rule.find()) {
      declarations.append(rule.group(1)).append(';');
    }
    return declarations.toString();
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
