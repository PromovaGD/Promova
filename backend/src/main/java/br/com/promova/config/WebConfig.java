package br.com.promova.config;

import br.com.promova.auth.AuthInterceptor;
import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  private final AuthService authService;
  private final AuthTokenResolver authTokenResolver;

  public WebConfig(AuthService authService, AuthTokenResolver authTokenResolver) {
    this.authService = authService;
    this.authTokenResolver = authTokenResolver;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(new AuthInterceptor(authService, authTokenResolver))
        .addPathPatterns(
            "/auth/**",
            "/analyses",
            "/analyses/**",
            "/analyze",
            "/evidences/**",
            "/api/github/**",
            "/admin",
            "/admin/**");
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOriginPatterns("http://localhost:*")
        .allowedMethods("*")
        .allowedHeaders("*");
  }
}
