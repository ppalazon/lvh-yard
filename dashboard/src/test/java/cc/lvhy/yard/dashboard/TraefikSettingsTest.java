package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import cc.lvhy.yard.dashboard.reachability.TraefikSettings;
import java.net.URI;
import org.junit.jupiter.api.Test;

class TraefikSettingsTest {

  @Test
  void adminEntrypointIsOnLoopbackWhereverWebListens() {
    TraefikSettings settings = new TraefikSettings(true, "192.168.1.5", 80, 8090);

    assertThat(settings.adminUrl()).isEqualTo(URI.create("http://127.0.0.1:8090"));
  }
}
