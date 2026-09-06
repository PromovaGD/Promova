package br.com.promova.analysis.engine.openrouter;

import br.com.promova.analysis.engine.ai.AiChatClient;
import br.com.promova.analysis.engine.ai.AiChatMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@ConditionalOnProperty(name = "promova.analysis.engine", havingValue = "openrouter")
public class OpenRouterChatClient implements AiChatClient {
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final String apiKey;
  private final String endpoint;
  private final String model;
  private final String siteUrl;
  private final String appName;
  private final int maxTokens;
  private final double temperature;
  private final int readTimeoutSeconds;

  public OpenRouterChatClient(
      ObjectMapper objectMapper,
      @Value("${openrouter.api-key:}") String apiKey,
      @Value("${openrouter.base-url:https://openrouter.ai/api/v1}") String baseUrl,
      @Value("${openrouter.model:meta-llama/llama-3.3-70b-instruct:free}") String model,
      @Value("${openrouter.site-url:http://localhost:4173}") String siteUrl,
      @Value("${openrouter.app-name:Promova}") String appName,
      @Value("${openrouter.max-tokens:800}") int maxTokens,
      @Value("${openrouter.temperature:0.2}") double temperature,
      @Value("${openrouter.connect-timeout-seconds:10}") int connectTimeoutSeconds,
      @Value("${openrouter.read-timeout-seconds:30}") int readTimeoutSeconds) {
    this.objectMapper = objectMapper;
    this.apiKey = apiKey;
    this.endpoint = baseUrl + "/chat/completions";
    this.model = model;
    this.siteUrl = siteUrl;
    this.appName = appName;
    this.maxTokens = maxTokens;
    this.temperature = temperature;
    this.readTimeoutSeconds = readTimeoutSeconds;
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("OPENROUTER_API_KEY must be configured for the OpenRouter engine");
    }
    if (model == null || model.isBlank()) {
      throw new IllegalStateException("OPENROUTER_MODEL must be configured for the OpenRouter engine");
    }
    if (connectTimeoutSeconds <= 0 || readTimeoutSeconds <= 0) {
      throw new IllegalStateException("OpenRouter timeouts must be positive");
    }
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
            .build();
  }

  @Override
  public String complete(List<AiChatMessage> messages) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(endpoint))
              .timeout(Duration.ofSeconds(readTimeoutSeconds))
              .headers(HttpHeaders.CONTENT_TYPE, "application/json")
              .headers(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim())
              .headers("HTTP-Referer", siteUrl)
              .headers("X-OpenRouter-Title", appName)
              .POST(HttpRequest.BodyPublishers.ofString(requestBody(messages)))
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() >= 400) {
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY, "OpenRouter request failed with status " + response.statusCode());
      }

      return assistantContent(response.body());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "OpenRouter request was interrupted", exception);
    } catch (IOException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Could not call OpenRouter API", exception);
    }
  }

  private String requestBody(List<AiChatMessage> messages) throws IOException {
    ObjectNode body = objectMapper.createObjectNode();
    body.put("model", model);
    body.put("temperature", temperature);
    body.put("max_tokens", maxTokens);
    body.set("response_format", responseFormat());

    ArrayNode messageNodes = body.putArray("messages");
    for (AiChatMessage message : messages) {
      ObjectNode messageNode = messageNodes.addObject();
      messageNode.put("role", message.role());
      messageNode.put("content", message.content());
    }

    return objectMapper.writeValueAsString(body);
  }

  private ObjectNode responseFormat() {
    ObjectNode responseFormat = objectMapper.createObjectNode();
    responseFormat.put("type", "json_object");
    return responseFormat;
  }

  private String assistantContent(String responseBody) throws IOException {
    JsonNode response = objectMapper.readTree(responseBody);
    String content = response.path("choices").path(0).path("message").path("content").asText();

    if (content == null || content.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenRouter returned an empty response");
    }

    return content;
  }
}
