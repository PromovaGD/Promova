package br.com.promova.github.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "github.api.token=stub-server-secret",
      "github.sync.lookback-days=90",
      "github.sync.page-size=2",
      "github.sync.max-pages=10"
    })
@ActiveProfiles("test")
class GithubConnectionHttpSmokeIntegrationTest {
  private static final String SERVER_TOKEN = "stub-server-secret";
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  private static final List<Integer> REQUESTED_PAGES = new ArrayList<>();
  private static final List<String> UPSTREAM_AUTHORIZATION = new ArrayList<>();
  private static final HttpServer GITHUB_STUB = createGithubStub();

  @Autowired private ObjectMapper objectMapper;
  @LocalServerPort private int applicationPort;

  @DynamicPropertySource
  static void githubProperties(DynamicPropertyRegistry registry) {
    registry.add("github.api.base-url", GithubConnectionHttpSmokeIntegrationTest::githubBaseUrl);
  }

  @AfterAll
  static void stopGithubStub() {
    GITHUB_STUB.stop(0);
  }

  @Test
  void runsTheAuthenticatedSettingsSyncRepeatAndSafeUpstreamErrorFlowOverHttp() throws Exception {
    String email = "http-smoke-" + UUID.randomUUID() + "@example.com";
    JsonNode auth =
        json(
            request(
                "POST",
                "/auth/register",
                null,
                "{\"name\":\"HTTP Smoke\",\"email\":\""
                    + email
                    + "\",\"password\":\"senha123\"}"));
    String sessionToken = auth.path("token").asText();
    assertThat(sessionToken).isNotBlank();

    HttpResponse<String> initialSettings = request("GET", "/api/github/settings", sessionToken, null);
    assertThat(initialSettings.statusCode()).isEqualTo(200);
    assertThat(json(initialSettings).path("configured").asBoolean()).isFalse();

    HttpResponse<String> savedSettings =
        request(
            "PUT",
            "/api/github/settings",
            sessionToken,
            "{\"repoSlug\":\"acme/project\",\"authorLogin\":\"octocat\"}");
    assertThat(savedSettings.statusCode()).isEqualTo(200);
    assertThat(json(savedSettings).path("configured").asBoolean()).isTrue();

    HttpResponse<String> tested = request("POST", "/api/github/settings/test", sessionToken, "{}");
    assertThat(tested.statusCode()).isEqualTo(200);
    assertThat(json(tested).path("ok").asBoolean()).isTrue();

    JsonNode firstSync = json(request("POST", "/api/github/sync", sessionToken, "{}"));
    JsonNode secondSync = json(request("POST", "/api/github/sync", sessionToken, "{}"));
    assertThat(firstSync.path("discovered").asInt()).isEqualTo(3);
    assertThat(firstSync.path("created").asInt()).isEqualTo(3);
    assertThat(firstSync.path("existing").asInt()).isZero();
    assertThat(firstSync.path("failed").asInt()).isZero();
    assertThat(secondSync.path("discovered").asInt()).isEqualTo(3);
    assertThat(secondSync.path("created").asInt()).isZero();
    assertThat(secondSync.path("existing").asInt()).isEqualTo(3);
    assertThat(secondSync.path("failed").asInt()).isZero();
    assertThat(firstSync.toString()).doesNotContain(SERVER_TOKEN);
    assertThat(secondSync.toString()).doesNotContain(SERVER_TOKEN);

    HttpResponse<String> pendingResponse =
        request("GET", "/evidences?status=PENDING", sessionToken, null);
    assertThat(pendingResponse.statusCode()).isEqualTo(200);
    JsonNode pending = json(pendingResponse);
    assertThat(pending).hasSize(3);
    assertThat(pending).allMatch(item -> "GitHub".equals(item.path("source").asText()));

    request(
        "PUT",
        "/api/github/settings",
        sessionToken,
        "{\"repoSlug\":\"forbidden/project\",\"authorLogin\":\"octocat\"}");
    HttpResponse<String> forbidden = request("POST", "/api/github/sync", sessionToken, "{}");
    assertThat(forbidden.statusCode()).isEqualTo(403);
    assertThat(forbidden.body()).contains("GitHub denied access");
    assertThat(forbidden.body()).doesNotContain("github_response");
    assertThat(forbidden.body()).doesNotContain(SERVER_TOKEN);

    JsonNode failedSettings =
        json(request("GET", "/api/github/settings", sessionToken, null));
    assertThat(failedSettings.path("lastSyncOutcome").asText()).isEqualTo("FAILED_UPSTREAM_403");
    assertThat(REQUESTED_PAGES).containsExactly(1, 2, 1, 2);
    assertThat(UPSTREAM_AUTHORIZATION).isNotEmpty().allMatch(value -> value.equals("Bearer " + SERVER_TOKEN));
  }

  private HttpResponse<String> request(String method, String path, String token, String body)
      throws IOException, InterruptedException {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(applicationBaseUrl() + path));
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    if (body == null) {
      builder.method(method, HttpRequest.BodyPublishers.noBody());
    } else {
      builder.header("Content-Type", "application/json");
      builder.method(method, HttpRequest.BodyPublishers.ofString(body));
    }
    return HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode json(HttpResponse<String> response) throws IOException {
    return objectMapper.readTree(response.body());
  }

  private static HttpServer createGithubStub() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/",
          GithubConnectionHttpSmokeIntegrationTest::handleGithubRequest);
      server.start();
      return server;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private static void handleGithubRequest(HttpExchange exchange) throws IOException {
    UPSTREAM_AUTHORIZATION.add(exchange.getRequestHeaders().getFirst("Authorization"));
    String path = exchange.getRequestURI().getPath();
    if (path.equals("/repos/acme/project")) {
      respond(exchange, 200, "{\"full_name\":\"acme/project\"}");
      return;
    }
    if (path.equals("/repos/acme/project/pulls")) {
      int page = page(exchange.getRequestURI().getQuery());
      REQUESTED_PAGES.add(page);
      if (page == 1) {
        respond(exchange, 200, "[" + pull(100) + "," + pull(101) + "]");
      } else if (page == 2) {
        respond(exchange, 200, "[" + pull(102) + "]");
      } else {
        respond(exchange, 200, "[]");
      }
      return;
    }
    if (path.equals("/repos/forbidden/project/pulls")) {
      respond(exchange, 403, "{\"message\":\"forbidden\"}");
      return;
    }
    respond(exchange, 404, "{\"message\":\"not found\"}");
  }

  private static String pull(int number) {
    return
        "{\"number\":"
            + number
            + ",\"title\":\"Improve service "
            + number
            + "\",\"state\":\"closed\",\"merged_at\":\"2026-08-08T10:00:00Z\","
            + "\"closed_at\":\"2026-08-08T10:00:00Z\",\"html_url\":\"https://github.com/acme/project/pull/"
            + number
            + "\",\"user\":{\"login\":\"octocat\"},\"updated_at\":\"2026-08-08T10:00:00Z\","
            + "\"created_at\":\"2026-08-07T10:00:00Z\",\"body\":\"Added tests\"}";
  }

  private static int page(String query) {
    if (query == null) {
      return 0;
    }
    for (String part : query.split("&")) {
      String[] pair = part.split("=", 2);
      if (pair.length == 2 && pair[0].equals("page")) {
        return Integer.parseInt(pair[1]);
      }
    }
    return 0;
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (var output = exchange.getResponseBody()) {
      output.write(bytes);
    }
  }

  private static String githubBaseUrl() {
    return "http://127.0.0.1:" + GITHUB_STUB.getAddress().getPort();
  }

  private String applicationBaseUrl() {
    return "http://127.0.0.1:" + applicationPort;
  }
}
