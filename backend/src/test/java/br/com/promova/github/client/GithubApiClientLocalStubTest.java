package br.com.promova.github.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.promova.github.support.GithubApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GithubApiClientLocalStubTest {
  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void usesTheConfiguredRelativePathAndServerTokenWithoutExposingIt() throws Exception {
    AtomicReference<String> authorization = new AtomicReference<>();
    startServer(
        exchange -> {
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          respond(exchange, 200, "{\"full_name\":\"acme/project\"}");
        });

    String secret = "server-secret";
    var response = new GithubApiClient(new ObjectMapper(), baseUrl() + "/", secret).get("/repos/acme/project");

    assertThat(response.path("full_name").asText()).isEqualTo("acme/project");
    assertThat(authorization).hasValue("Bearer " + secret);
  }

  @Test
  void turnsMalformedErrorBodiesIntoStatusOnlyExceptions() throws Exception {
    startServer(exchange -> respond(exchange, 403, "not-json"));

    assertThatThrownBy(
            () -> new GithubApiClient(new ObjectMapper(), baseUrl(), "do-not-leak").get("/repos/acme/project"))
        .isInstanceOf(GithubApiException.class)
        .satisfies(
            error -> {
              GithubApiException githubError = (GithubApiException) error;
              assertThat(githubError.statusCode()).isEqualTo(403);
              assertThat(githubError.getMessage()).doesNotContain("do-not-leak");
            });
  }

  private void startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/repos/acme/project", handler);
    server.start();
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  private void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, bytes.length);
    try (var output = exchange.getResponseBody()) {
      output.write(bytes);
    }
  }
}
