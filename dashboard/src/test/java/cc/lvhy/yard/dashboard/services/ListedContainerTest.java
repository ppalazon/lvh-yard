package cc.lvhy.yard.dashboard.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ListedContainerTest {
  @ParameterizedTest
  @CsvSource({"My_Web,my-web", "shop-web-1,shop-web-1", "Front_End,front-end"})
  void proposesAValidServiceName(String name, String expected) {
    assertThat(ListedContainer.proposedService(Optional.of(name), "container-1"))
        .isEqualTo(expected);
    assertThat(ListedContainer.proposedService(Optional.empty(), name)).isEqualTo(expected);
  }

  @Test
  void trimsAfterTruncatingAndFallsBackWhenComposeNameHasNoLettersOrDigits() {
    assertThat(
            ListedContainer.proposedService(Optional.of("a".repeat(62) + "__suffix"), "fallback"))
        .isEqualTo("a".repeat(62));
    assertThat(ListedContainer.proposedService(Optional.of("___"), "fallback"))
        .isEqualTo("fallback");
  }
}
