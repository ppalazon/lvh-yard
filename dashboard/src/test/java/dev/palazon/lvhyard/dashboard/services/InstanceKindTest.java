package dev.palazon.lvhyard.dashboard.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** An Instance Priority as a Publication request gives it. */
class InstanceKindTest {

  @Test
  void priorityKeepsTheOrderItIsGiven() {
    assertThat(InstanceKind.priority(List.of("bound", "static")))
        .containsExactly(InstanceKind.BOUND, InstanceKind.STATIC);
    assertThat(InstanceKind.priority(List.of())).isEmpty();
  }

  @Test
  void unknownKindIsRefusedWithTheKindsThatExist() {
    assertThatThrownBy(() -> InstanceKind.priority(List.of("static", "docker")))
        .isInstanceOf(InvalidPriorityException.class)
        .hasMessage(
            "The Instance Priority has the unknown kind docker. The kinds are static, bound and"
                + " alias.");
  }

  @Test
  void repeatedKindIsRefused() {
    assertThatThrownBy(() -> InstanceKind.priority(List.of("bound", "static", "bound")))
        .isInstanceOf(InvalidPriorityException.class)
        .hasMessage("The Instance Priority has the kind bound more than once.");
  }
}
