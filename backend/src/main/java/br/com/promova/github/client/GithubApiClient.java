package br.com.promova.github.client;

import br.com.promova.github.support.GithubApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GithubApiClient {
  private static final String BASE_URL = "https://api.github.com";
  private static final String API_VERSION = "2022-11-28";
  private static final String USER_AGENT = "promova-github-extract/1";

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final String baseUrl;
  private final String token;

  public GithubApiClient(
      ObjectMapper objectMapper,
      @Value("${github.api.base-url:https://api.github.com}") String baseUrl,
      @Value("${github.api.token:}") String token) {
    this.objectMapper = objectMapper;
    this.baseUrl = baseUrl;
    this.token = token;
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
  }

  public JsonNode get(String pathAndQuery) {
    HttpRequest.Builder requestBuilder =
        HttpRequest.newBuilder(URI.create(baseUrl + pathAndQuery))
            .timeout(Duration.ofSeconds(20))
            .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
            .header(HttpHeaders.USER_AGENT, USER_AGENT)
            .GET();

    if (token != null && !token.isBlank()) {
      requestBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token.trim());
    }

    try {
      HttpResponse<String> response =
          httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
      JsonNode body = parseBody(response.body());

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
}
