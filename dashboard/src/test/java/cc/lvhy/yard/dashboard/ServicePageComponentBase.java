package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

/** The paths and checks that the Service page tests share. */
abstract class ServicePageComponentBase extends DashboardComponentTest {

  /** The page of a Service of this Workstation. */
  static String pagePath(String service) {
    return "/services/ws-test/" + service;
  }

  /** The page of a Service of the other Workstation. */
  static String remotePagePath(String service) {
    return "/services/" + RemoteWorkstation.NAME + "/" + service;
  }

  /** Checks that {@code first} is in {@code text}, and {@code second} comes after it. */
  static void assertInOrder(String text, String first, String second) {
    assertThat(text.indexOf(first)).as(first).isNotNegative();
    assertThat(text.indexOf(second)).as(second).isGreaterThan(text.indexOf(first));
  }
}
