package cc.lvhy.yard.dashboard.web;

import static cc.lvhy.yard.dashboard.events.EventBroadcaster.CONTAINERS_CHANGED;
import static cc.lvhy.yard.dashboard.events.EventBroadcaster.YARD_CHANGED;
import static org.assertj.core.api.Assertions.assertThat;

import htmlflow.HtmlFlow;
import java.util.List;
import org.junit.jupiter.api.Test;

class LayoutTest {

  @Test
  void refreshedRegionListsEveryEventItRefreshesOn() {
    StringBuilder out = new StringBuilder();
    HtmlFlow.doc(out)
        .setIndented(false)
        .html()
        .body()
        .main()
        .of(main ->
            Layout.refreshed(
                main,
                "containers",
                "/containers",
                List.of(CONTAINERS_CHANGED, YARD_CHANGED),
                div -> div.text("content")))
        .__()
        .__()
        .__();
    assertThat(out.toString())
        .contains("hx-trigger=\"containers-changed from:body, yard-changed from:body, every 30s\"");
  }

  @Test
  void refreshedRegionWithoutEventsPollsEveryFiveSeconds() {
    StringBuilder out = new StringBuilder();
    HtmlFlow.doc(out)
        .setIndented(false)
        .html()
        .body()
        .main()
        .of(main -> Layout.refreshed(main, "status", "/status", div -> div.text("content")))
        .__()
        .__()
        .__();
    assertThat(out.toString()).contains("hx-trigger=\"every 5s\"");
  }
}
