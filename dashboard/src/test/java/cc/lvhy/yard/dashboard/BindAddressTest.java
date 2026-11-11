package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BindAddressTest {

  @Test
  void theWildcardIsReachedThroughLoopback() {
    assertThat(BindAddress.host("0.0.0.0")).isEqualTo("127.0.0.1");
  }

  @Test
  void anyOtherAddressIsReachedAsItIs() {
    assertThat(BindAddress.host("127.0.0.1")).isEqualTo("127.0.0.1");
    assertThat(BindAddress.host("192.168.1.5")).isEqualTo("192.168.1.5");
  }
}
