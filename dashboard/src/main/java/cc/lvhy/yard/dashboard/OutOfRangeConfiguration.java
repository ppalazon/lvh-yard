package cc.lvhy.yard.dashboard;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
class OutOfRangeConfiguration {

  @Bean
  OutOfRange outOfRange(Environment environment) {
    return Binder.get(environment).bindOrCreate("lvh-yard.out-of-range", OutOfRange.class);
  }
}
