package test

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean

@SpringBootConfiguration
@EnableAutoConfiguration
class SingleServiceConfig {

  @Bean
  SingleServiceRoute serviceRoute() {
    return new SingleServiceRoute()
  }
}
