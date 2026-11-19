package cc.lvhy.yard.dashboard;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DashboardApplication {

  /** The time that every class reads, so a test can set it. */
  @Bean
  Clock clock() {
    return Clock.systemDefaultZone();
  }

  public static void main(String[] args) {
    SpringApplication.run(DashboardApplication.class, args);
  }
}
