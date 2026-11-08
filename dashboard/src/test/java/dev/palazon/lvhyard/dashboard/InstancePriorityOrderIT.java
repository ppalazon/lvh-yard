package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** An Instance Priority set for one Service, which changes the kind its Route prefers. */
class InstancePriorityOrderIT extends InstancePriorityTestSupportIT {

  @Test
  void boundBeforeStaticRoutesToTheBoundInstance() {
    String web = newServiceName("web");
    staticInstance(web);
    String bound = boundInstance(web);

    put(publicationPath(web), Map.of("priority", List.of("bound", "static")));

    awaitRoutedTo(web, bound);
    assertThat(summaryCard(getPage("/services/ws-test/" + web).getBody(), "routing"))
        .contains("Bound")
        .contains("Static waiting");
  }

  @Test
  void publicationWrittenBeforeInstancePriorityRoutesWithTheDefaultOrder() throws Exception {
    String web = newServiceName("web");
    String terminal = staticInstance(web);
    boundInstance(web);

    // A Publication as LVH Yard stored it before Instance Priority: its Scope alone.
    consulContainer.execInContainer("consul", "kv", "put", "lvh-yard/publications/" + web, "local");

    awaitRoutedTo(web, terminal);
  }

  @Test
  void kindLeftOutIsNotWaiting() {
    String web = newServiceName("web");
    staticInstance(web);
    String bound = boundInstance(web);

    put(publicationPath(web), Map.of("priority", List.of("bound")));

    awaitRoutedTo(web, bound);
    assertThat(summaryCard(getPage("/services/ws-test/" + web).getBody(), "routing"))
        .contains("Bound")
        .doesNotContain("waiting");
  }
}
