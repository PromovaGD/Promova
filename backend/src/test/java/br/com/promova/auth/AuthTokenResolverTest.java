package br.com.promova.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthTokenResolverTest {
  private final AuthTokenResolver resolver = new AuthTokenResolver();

  @Test
  void resolvesBearerTokenWithoutLoggingOrTransformingIt() {
    assertThat(resolver.resolve("Bearer session-token")).isEqualTo("session-token");
    assertThat(resolver.resolve("bearer session-token")).isEqualTo("session-token");
  }

  @Test
  void rejectsMissingOrMalformedAuthorizationHeaders() {
    assertThat(resolver.resolve((String) null)).isNull();
    assertThat(resolver.resolve(" ")).isNull();
    assertThat(resolver.resolve("Basic session-token")).isNull();
    assertThat(resolver.resolve("Bearer")).isNull();
    assertThat(resolver.resolve("Bearer ")).isNull();
    assertThat(resolver.resolve("Bearer session-token extra")).isNull();
  }
}
