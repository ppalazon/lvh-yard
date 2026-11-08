package dev.palazon.lvhyard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The Aliases page, where this Workstation's Aliases are added, seen and removed. */
class AliasesPageComponentTest extends DashboardComponentTest {

  @Test
  void catalogLinksToTheAliasesPage() {
    assertThat(getPage("/").getBody()).contains("href=\"/aliases\"");
  }
}
