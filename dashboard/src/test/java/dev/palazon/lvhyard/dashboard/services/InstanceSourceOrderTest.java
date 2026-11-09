package dev.palazon.lvhyard.dashboard.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** How the stored order of Instance Source IDs meets the sources a Service has now. */
class InstanceSourceOrderTest {

  @Test
  void sourcesFollowTheStoredOrder() {
    assertThat(InstanceSource.order(List.of("b", "c", "a"), Set.of("a", "b", "c")))
        .containsExactly("b", "c", "a");
  }

  @Test
  void sourceThatTheOrderDoesNotNameGoesToTheEnd() {
    assertThat(InstanceSource.order(List.of("b"), Set.of("a", "b", "c")))
        .containsExactly("b", "a", "c");
  }

  @Test
  void idWhoseSourceIsGoneIsSkipped() {
    assertThat(InstanceSource.order(List.of("gone", "b", "a"), Set.of("a", "b")))
        .containsExactly("b", "a");
  }

  @Test
  void repeatedIdCountsOnce() {
    assertThat(InstanceSource.order(List.of("a", "b", "a"), Set.of("a", "b")))
        .containsExactly("a", "b");
  }
}
