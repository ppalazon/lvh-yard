package cc.lvhy.yard.dashboard.web;

import static org.assertj.core.api.Assertions.assertThat;

import cc.lvhy.yard.dashboard.status.Part;
import cc.lvhy.yard.dashboard.status.PartState;
import htmlflow.HtmlFlow;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The TLS badges that sit next to a part's title. */
class StatusViewsTest {

  private static String badges(Part part) {
    StringBuilder out = new StringBuilder();
    HtmlFlow.doc(out).setIndented(false).div().of(div -> StatusViews.tlsBadges(div, part)).__();
    return out.toString();
  }

  private static Part part(String name, PartState state, List<Part.Detail> details, String... tls) {
    return new Part(name, state, "Reason.", details, List.of(tls));
  }

  @Test
  void traefikShowsEachTlsPartGreenWhenOnAndRedWhenOff() {
    String html = badges(part("Traefik", PartState.OK, List.of(), "HTTPS", "Mutual TLS"));

    assertThat(html)
        .containsPattern("data-variant=\"success\"[^>]*><span[^>]*>✓</span> HTTPS")
        .containsPattern("data-variant=\"destructive\"[^>]*><span[^>]*>✕</span> Client Certificate")
        .containsPattern("data-variant=\"success\"[^>]*><span[^>]*>✓</span> Mutual TLS");
  }

  @Test
  void consulShowsTlsRedWhenOff() {
    assertThat(badges(part("Consul", PartState.OK, List.of())))
        .containsPattern("data-variant=\"destructive\"[^>]*><span[^>]*>✕</span> TLS");
  }

  @Test
  void aPartThatDoesNotAnswerShowsNoBadgeBecauseItCannotSay() {
    assertThat(badges(part("Consul", PartState.FAILING, List.of()))).doesNotContain("badge");
  }

  @Test
  void aPartWithoutTlsShowsNoBadge() {
    assertThat(badges(part("Docker", PartState.OK, List.of()))).doesNotContain("badge");
  }
}
