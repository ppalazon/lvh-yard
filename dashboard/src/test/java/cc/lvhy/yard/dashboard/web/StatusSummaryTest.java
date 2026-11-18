package cc.lvhy.yard.dashboard.web;

import static org.assertj.core.api.Assertions.assertThat;

import cc.lvhy.yard.dashboard.status.Part;
import cc.lvhy.yard.dashboard.status.PartState;
import cc.lvhy.yard.dashboard.status.Status;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What the header shows for a part that uses TLS. */
class StatusSummaryTest {

  private static Part part(String name, PartState state, String... tls) {
    return new Part(name, state, "Reason.", List.of(), List.of(tls));
  }

  private static String dot(String summary, String id) {
    int start = summary.indexOf("id=\"dot-" + id + "\"");
    assertThat(start).as("dot %s", id).isNotNegative();
    return summary.substring(start, summary.indexOf("</span>", start));
  }

  @Test
  void anOkPartThatUsesTlsShowsALockInsteadOfTheTick() {
    String summary =
        StatusSummary.render(
            Status.of(
                List.of(
                    part("Consul", PartState.OK, "TLS"),
                    part("Traefik", PartState.OK, "HTTPS", "Mutual TLS"),
                    part("Docker", PartState.OK))));

    assertThat(dot(summary, "consul")).contains("<svg").doesNotContain("✓");
    assertThat(dot(summary, "traefik")).contains("<svg").contains("Uses HTTPS, Mutual TLS.");
    assertThat(dot(summary, "docker")).contains("✓").doesNotContain("<svg");
  }

  @Test
  void aPartThatUsesTlsButIsNotOkKeepsItsStateSymbol() {
    String summary =
        StatusSummary.render(
            Status.of(
                List.of(
                    part("Consul", PartState.WARNING, "TLS"),
                    part("Traefik", PartState.FAILING, "HTTPS"))));

    assertThat(dot(summary, "consul")).contains(">!").doesNotContain("<svg");
    assertThat(dot(summary, "traefik")).contains(">✕").doesNotContain("<svg");
  }
}
