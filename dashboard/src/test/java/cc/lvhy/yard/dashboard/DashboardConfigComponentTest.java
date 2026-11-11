package cc.lvhy.yard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import cc.lvhy.yard.dashboard.consul.FakeConsul;
import cc.lvhy.yard.dashboard.docker.FakeDocker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.client.RestClient;

class DashboardConfigComponentTest {

  @TempDir Path folder;

  @Test
  void externalSettingsApplyAfterRestartAndEnvPlaceholdersStillWin() throws IOException {
    Path config = folder.resolve("application.yaml");
    Path env = folder.resolve(".env");
    Files.writeString(env, "");
    Files.writeString(config, "# lvh-yard:\n#   workstation:\n#     base-domain: ${YARD_BASE_DOMAIN:example.test}\n");
    assertThat(catalog(config, env)).contains("base domain lvh.me.");

    Files.writeString(config, "lvh-yard:\n  workstation:\n    base-domain: ${YARD_BASE_DOMAIN:example.test}\n");
    assertThat(catalog(config, env)).contains("base domain example.test.");

    Files.writeString(env, "YARD_BASE_DOMAIN=env.test\n");
    assertThat(catalog(config, env)).contains("base domain env.test.");
  }

  private String catalog(Path config, Path env) {
    try (ConfigurableApplicationContext dashboard =
        new SpringApplicationBuilder(DashboardApplication.class)
            .initializers(context -> {
              context.getBeanFactory().registerSingleton(
                  RemoteWorkstation.SkipTestConfigurations.class.getName(),
                  new RemoteWorkstation.SkipTestConfigurations());
              GenericApplicationContext beans = (GenericApplicationContext) context;
              beans.registerBean("fakeConsul", FakeConsul.class, () -> new FakeConsul("ws-config"),
                  bean -> bean.setPrimary(true));
              beans.registerBean("fakeDocker", FakeDocker.class, FakeDocker::new,
                  bean -> bean.setPrimary(true));
            })
            .run(
                "--spring.config.additional-location=" + config.toUri(),
                "--spring.config.import=" + env.toUri() + "[.properties]",
                "--spring.main.banner-mode=off",
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--lvh-yard.workstation.name=ws-config",
                "--lvh-yard.consul.url=http://127.0.0.1:1",
                "--lvh-yard.consul.peers=",
                "--lvh-yard.docker.host=unix:///fake/docker.sock",
                "--lvh-yard.backups.path=" + folder.resolve("backups"))) {
      return RestClient.create("http://127.0.0.1:"
              + dashboard.getEnvironment().getRequiredProperty("local.server.port"))
          .get().uri("/").retrieve().body(String.class);
    }
  }
}
