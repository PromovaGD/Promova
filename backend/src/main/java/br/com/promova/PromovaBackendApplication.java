package br.com.promova;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import java.time.Clock;

@SpringBootApplication
public class PromovaBackendApplication {
  public static void main(String[] args) {
    SpringApplication.run(PromovaBackendApplication.class, args);
  }

  @Bean
  Clock applicationClock() {
    return Clock.systemUTC();
  }
}
