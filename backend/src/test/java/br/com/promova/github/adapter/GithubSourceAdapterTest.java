package br.com.promova.github.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.promova.github.client.GithubApiClient;
import br.com.promova.github.service.GithubPullRequestService;
import br.com.promova.source.NormalizedEvidence;
import br.com.promova.source.SourceAdapterRequest;
import br.com.promova.source.SourcePageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GithubSourceAdapterTest {
  private static final String SECRET = "server-secret-that-must-not-leak";
  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void normalizesMatchingGithubItemsAndKeepsTheProviderPageBoundary() throws Exception {
    startServer(
        "[%s,%s,%s,%s]"
            .formatted(
                pull(77, "octocat", "2026-08-08T10:00:00Z", "2026-08-08T10:00:00Z", "Implemented feature " + SECRET),
                pull(78, "other", "2026-08-08T10:00:00Z", "2026-08-08T09:00:00Z", "Other author"),
                pull(79, "octocat", null, "2026-08-08T08:00:00Z", "Not merged"),
                pull(80, "octocat", "2026-07-01T10:00:00Z", "2026-07-01T10:00:00Z", "Outside lookback")));

    GithubSourceAdapter adapter =
        new GithubSourceAdapter(
            new GithubPullRequestService(
                new GithubApiClient(new ObjectMapper(), baseUrl(), SECRET)));

    SourcePageResult result =
        adapter.discover(
            new SourceAdapterRequest(
                "acme/project", "octocat", Instant.parse("2026-08-01T00:00:00Z"), 10, 1));

    assertThat(result.failedItems()).isZero();
    assertThat(result.items()).hasSize(1);
    assertThat(result.hasPotentialNextPage()).isFalse();
    assertThat(result.oldestObservedAt()).isEqualTo(Instant.parse("2026-07-01T10:00:00Z"));

    NormalizedEvidence evidence = result.items().get(0);
    assertThat(evidence.source()).isEqualTo("GitHub");
    assertThat(evidence.externalId()).isEqualTo("github:acme/project#77");
    assertThat(evidence.sourceMeta()).isEqualTo("PR #77 - acme/project - Improve service 77");
    assertThat(evidence.evidenceText()).contains("Implemented feature", "PR #77");
    assertThat(evidence.sourceUrl()).isEqualTo("https://github.com/acme/project/pull/77");
    assertThat(evidence.author()).isEqualTo("octocat");
    assertThat(evidence.occurredAt()).isEqualTo(Instant.parse("2026-08-08T10:00:00Z"));
    assertThat(evidence.providerMetadata())
        .containsEntry("repository", "acme/project")
        .containsEntry("number", "77")
        .containsEntry("author", "octocat")
        .doesNotContainKey("token");
    assertThat(evidence.toString()).doesNotContain(SECRET);
  }

  @Test
  void countsMalformedProviderItemsWithoutAbortingValidNormalization() throws Exception {
    startServer(
        "[%s,{\"number\":\"not-a-number\"},%s]"
            .formatted(
                pull(81, "octocat", "2026-08-08T10:00:00Z", "2026-08-08T10:00:00Z", "First"),
                pull(82, "octocat", "2026-08-08T09:00:00Z", "2026-08-08T09:00:00Z", "Second")));

    GithubSourceAdapter adapter =
        new GithubSourceAdapter(
            new GithubPullRequestService(
                new GithubApiClient(new ObjectMapper(), baseUrl(), SECRET)));

    SourcePageResult result =
        adapter.discover(
            new SourceAdapterRequest(
                "acme/project", "octocat", Instant.parse("2026-08-01T00:00:00Z"), 10, 1));

    assertThat(result.items()).extracting(NormalizedEvidence::externalId)
        .containsExactly("github:acme/project#81", "github:acme/project#82");
    assertThat(result.failedItems()).isEqualTo(1);
  }

  @Test
  void keepsTheLegacySinglePullRequestCaptureShapeAfterNormalization() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/repos/acme/project/pulls/83",
        exchange ->
            respond(
                exchange,
                200,
                "{\"number\":83,\"title\":\"Improve service 83\",\"state\":\"closed\","
                    + "\"merged_at\":\"2026-08-08T10:00:00Z\",\"closed_at\":\"2026-08-08T11:00:00Z\","
                    + "\"html_url\":\"https://github.com/acme/project/pull/83\","
                    + "\"user\":{\"login\":\"octocat\"},\"updated_at\":\"2026-08-08T11:00:00Z\","
                    + "\"created_at\":\"2026-08-07T10:00:00Z\",\"body\":\"Added tests\","
                    + "\"additions\":12,\"deletions\":3,\"changed_files\":1}"));
    server.createContext(
        "/repos/acme/project/pulls/83/files",
        exchange -> respond(exchange, 200, "[]"));
    server.start();

    GithubSourceAdapter adapter =
        new GithubSourceAdapter(
            new GithubPullRequestService(
                new GithubApiClient(new ObjectMapper(), baseUrl(), SECRET)));

    NormalizedEvidence evidence = adapter.fetchPullRequest("acme/project", 83, "octocat");

    assertThat(evidence.externalId()).isEqualTo("github:acme/project#83");
    assertThat(evidence.sourceMeta()).isEqualTo("PR #83 - acme/project");
    assertThat(evidence.evidenceText())
        .contains("Volume coletado via API (+12 -3 linhas, 1 arquivo(s)).", "Added tests");
    assertThat(evidence.sourceUrl()).isEqualTo("https://github.com/acme/project/pull/83");
    assertThat(evidence.author()).isEqualTo("octocat");
    assertThat(evidence.occurredAt()).isEqualTo(Instant.parse("2026-08-08T10:00:00Z"));
  }

  private void startServer(String body) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/repos/acme/project/pulls",
        exchange -> respond(exchange, 200, body));
    server.start();
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  private String pull(
      int number, String author, String mergedAt, String updatedAt, String body) {
    String merged = mergedAt == null ? "null" : "\"%s\"".formatted(mergedAt);
    return
        "{"
            + "\"number\":"
            + number
            + ",\"title\":\"Improve service "
            + number
            + "\",\"state\":\"closed\",\"merged_at\":"
            + merged
            + ",\"closed_at\":\"2026-08-08T11:00:00Z\","
            + "\"html_url\":\"https://github.com/acme/project/pull/"
            + number
            + "\",\"user\":{\"login\":\""
            + author
            + "\"},\"updated_at\":\""
            + updatedAt
            + "\",\"created_at\":\"2026-08-07T10:00:00Z\",\"body\":\""
            + body
            + "\"}";
  }

  private void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (var output = exchange.getResponseBody()) {
      output.write(bytes);
    }
  }
}
