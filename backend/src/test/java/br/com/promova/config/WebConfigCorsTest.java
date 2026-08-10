package br.com.promova.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

class WebConfigCorsTest {
  @Test
  void leavesCrossOriginRequestsDeniedWhenNoOriginIsConfigured() {
    CorsConfiguration configuration = corsConfiguration("");

    assertThat(configuration.getAllowedOrigins()).isEmpty();
    assertThat(configuration.getAllowedOriginPatterns()).isNullOrEmpty();
  }

  @Test
  void usesConfiguredPatternsForDevelopmentAndDeployment() {
    CorsConfiguration configuration =
        corsConfiguration("http://localhost:*, https://promova.example.com");

    assertThat(configuration.getAllowedOriginPatterns())
        .containsExactly("http://localhost:*", "https://promova.example.com");
  }

  private CorsConfiguration corsConfiguration(String origins) {
    TestCorsRegistry registry = new TestCorsRegistry();
    new WebConfig(mock(AuthService.class), mock(AuthTokenResolver.class), origins)
        .addCorsMappings(registry);
    return registry.configurations().get("/**");
  }

  private static final class TestCorsRegistry extends CorsRegistry {
    private Map<String, CorsConfiguration> configurations() {
      return getCorsConfigurations();
    }
  }
}
