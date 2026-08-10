package br.com.promova.github.client;

import br.com.promova.github.support.GithubApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GithubApiClient {
  private static final String API_VERSION = "2022-11-28";
  private static final String USER_AGENT = "promova-github-extract/1";
  private static final String REDACTED = "[REDACTED]";
  private static final Pattern SENSITIVE_FIELD =
      Pattern.compile(
          "(?i)(?:token|secret|authorization|password|credential|api[_-]?key|private[_-]?key|cookie)");

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final String baseUrl;
  private final String token;

  public GithubApiClient(
      ObjectMapper objectMapper,
      @Value("${github.api.base-url:https://api.github.com}") String baseUrl,
      @Value("${github.api.token:}") String token) {
    this.objectMapper = objectMapper;
    this.baseUrl = trimTrailingSlashes(baseUrl);
    this.token = token == null ? "" : token.trim();
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
  }

  public JsonNode get(String pathAndQuery) {
    String safePath = safePath(pathAndQuery);
    HttpRequest.Builder requestBuilder =
        HttpRequest.newBuilder(URI.create(baseUrl + safePath))
            .timeout(Duration.ofSeconds(20))
            .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
            .header(HttpHeaders.USER_AGENT, USER_AGENT)
            .GET();

    if (!token.isBlank()) {
      requestBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    try {
      HttpResponse<String> response =
          httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
      JsonNode body = redactSecrets(parseBody(response.body()));

      if (response.statusCode() >= 400) {
        throw new GithubApiException(
            response.statusCode(), body, "GitHub API responded with status " + response.statusCode());
      }

      return body;
    } catch (GithubApiException exception) {
      throw exception;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "GitHub request was interrupted", exception);
    } catch (IOException exception) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Could not reach GitHub API", exception);
    }
  }

  private JsonNode parseBody(String body) {
    if (body == null || body.isBlank()) {
      return NullNode.getInstance();
    }

    try {
      return objectMapper.readTree(body);
    } catch (IOException exception) {
      return NullNode.getInstance();
    }
  }

  /** Keeps an upstream payload from carrying the configured server token into adapter data. */
  private JsonNode redactSecrets(JsonNode node) {
    if (node == null || node.isNull()) {
      return NullNode.getInstance();
    }
    if (node.isTextual()) {
      return TextNode.valueOf(redactText(node.asText()));
    }
    if (node.isObject()) {
      ObjectNode safe = objectMapper.createObjectNode();
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        if (SENSITIVE_FIELD.matcher(field.getKey()).find()) {
          safe.put(field.getKey(), REDACTED);
        } else {
          safe.set(field.getKey(), redactSecrets(field.getValue()));
        }
      }
      return safe;
    }
    if (node.isArray()) {
      ArrayNode safe = objectMapper.createArrayNode();
      node.forEach(value -> safe.add(redactSecrets(value)));
      return safe;
    }
    return node;
  }

  private String redactText(String value) {
    if (token.isBlank() || value.isBlank()) {
      return value;
    }
    return value.replace(token, REDACTED);
  }

  private String safePath(String pathAndQuery) {
    if (pathAndQuery == null
        || pathAndQuery.isBlank()
        || !pathAndQuery.startsWith("/")
        || pathAndQuery.contains("://")) {
      throw new IllegalArgumentException("GitHub API path must be relative to the configured base URL");
    }
    return pathAndQuery;
  }

  private String trimTrailingSlashes(String value) {
    String normalized = value == null || value.isBlank() ? "https://api.github.com" : value.trim();
    while (normalized.endsWith("/") && normalized.length() > "https://".length()) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }
}
