package dev.palazon.lvhyard.dashboard;

import org.junit.jupiter.api.Test;

/**
 * The Instance Priority with two Instances of each kind: every passing Instance shares the Route.
 */
class InstancePriorityReplicasIT extends InstancePriorityTestSupportIT {

  @Test
  void twoStaticInstancesShareTheRouteThenTwoBoundInstancesWhenBothStaticOnesStop() {
    String web = newServiceName("web");
    String firstStatic = staticInstance(web);
    String secondStatic = staticInstance(web);
    String firstBound = boundInstance(web);
    String secondBound = boundInstance(web);
    publish(web);

    awaitRoutedTo(web, firstStatic, secondStatic);

    stopListeningOnLanAddress(port(firstStatic));
    stopListeningOnLanAddress(port(secondStatic));

    awaitRoutedTo(web, firstBound, secondBound);

    listenOnLanAddress(port(firstStatic));

    awaitRoutedTo(web, firstStatic);
  }
}
