package br.com.promova.config;

import br.com.promova.auth.AuthInterceptor;
import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.util.StringUtils;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  private final AuthService authService;
  private final AuthTokenResolver authTokenResolver;
  private final String allowedOrigins;

  public WebConfig(
      AuthService authService,
      AuthTokenResolver authTokenResolver,
      @Value("${promova.cors.allowed-origins:}") String allowedOrigins) {
    this.authService = authService;
    this.authTokenResolver = authTokenResolver;
    this.allowedOrigins = allowedOrigins;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(new AuthInterceptor(authService, authTokenResolver))
        .addPathPatterns(
            "/auth/**",
            "/analyses",
            "/analyses/**",
            "/insights",
            "/evidences/**",
            "/api/github/**",
            "/profile",
            "/profile/**",
            "/career-configuration",
            "/manager",
            "/manager/**");
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    CorsRegistration registration = registry.addMapping("/**");
    String[] origins =
        Arrays.stream(StringUtils.commaDelimitedListToStringArray(allowedOrigins))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .toArray(String[]::new);

    if (origins.length > 0) {
      registration.allowedOriginPatterns(origins);
    } else {
      registration.allowedOrigins();
    }

    registration.allowedMethods("*").allowedHeaders("*");
  }
}
