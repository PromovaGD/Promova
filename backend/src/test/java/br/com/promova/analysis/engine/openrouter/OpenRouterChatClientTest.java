package br.com.promova.analysis.engine.openrouter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenRouterChatClientTest {
  @Test
  void failsFastWhenTheApiKeyIsMissing() {
    assertThatThrownBy(
            () ->
                new OpenRouterChatClient(
                    new ObjectMapper(),
                    " ",
                    "https://openrouter.test/api/v1",
                    "test-model",
                    "https://promova.test",
                    "Promova",
                    800,
                    0.2,
                    10,
                    30))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("OPENROUTER_API_KEY must be configured for the OpenRouter engine");
  }

  @Test
  void rejectsInvalidTimeoutConfiguration() {
    assertThatThrownBy(
            () ->
                new OpenRouterChatClient(
                    new ObjectMapper(),
                    "secret",
                    "https://openrouter.test/api/v1",
                    "test-model",
                    "https://promova.test",
                    "Promova",
                    800,
                    0.2,
                    0,
                    30))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("OpenRouter timeouts must be positive");
  }
}
