package cc.lvhy.yard.dashboard.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/** A Peer as YARD_PEERS writes it, read into its host and its serf WAN port. */
class PeerTest {

  @Test
  void aPeerWithNoPortUsesTheSerfWanPort() {
    assertThat(Peer.parse("10.0.0.2")).isEqualTo(new Peer("10.0.0.2", 8302));
    assertThat(Peer.parse("ws-b.lan")).isEqualTo(new Peer("ws-b.lan", 8302));
  }

  @Test
  void aPeerWithAPortKeepsIt() {
    assertThat(Peer.parse("10.0.0.2:9302")).isEqualTo(new Peer("10.0.0.2", 9302));
  }

  @Test
  void anIpv6PeerIsBracketedWhenItHasAPort() {
    assertThat(Peer.parse("[fd00::1]:9302")).isEqualTo(new Peer("fd00::1", 9302));
    assertThat(Peer.parse("[fd00::1]")).isEqualTo(new Peer("fd00::1", 8302));
    assertThat(Peer.parse("fd00::1")).isEqualTo(new Peer("fd00::1", 8302));
  }

  @Test
  void aPeerWithABadPortCannotBeRead() {
    assertThatIllegalArgumentException().isThrownBy(() -> Peer.parse("10.0.0.2:serf"));
    assertThatIllegalArgumentException().isThrownBy(() -> Peer.parse("[fd00::1"));
  }
}
