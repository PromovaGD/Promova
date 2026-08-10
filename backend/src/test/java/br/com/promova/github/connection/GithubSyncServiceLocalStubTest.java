package br.com.promova.github.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.promova.evidence.EvidenceStatus;
import br.com.promova.evidence.dto.EvidenceResponse;
import br.com.promova.evidence.service.EvidenceCaptureResult;
import br.com.promova.evidence.service.EvidenceService;
import br.com.promova.github.adapter.GithubSourceAdapter;
import br.com.promova.github.client.GithubApiClient;
import br.com.promova.github.service.GithubPullRequestService;
import br.com.promova.github.support.GithubApiException;
import br.com.promova.github.support.GithubPayloadException;
import br.com.promova.source.NormalizedEvidence;
import br.com.promova.source.SourceEvidenceCaptureService;
import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GithubSyncServiceLocalStubTest {
  private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

  @Mock private GithubConnectionSettingsService settingsService;
  @Mock private SourceEvidenceCaptureService sourceEvidenceCaptureService;
  @Mock private EvidenceService evidenceService;

  private HttpServer server;
  private User user;
  private GithubConnectionSettings settings;

  @BeforeEach
  void setUp() {
    user = new User("Employee", "employee@example.com", "hash", UserRole.EMPLOYEE);
    ReflectionTestUtils.setField(user, "id", 7L);
    settings = new GithubConnectionSettings(user);
    settings.configure("acme/project", "octocat");
    when(settingsService.requireConfigured(user)).thenReturn(settings);
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void paginatesMatchingMergedPullRequestsAndSecondSyncReportsExistingRows() throws Exception {
    List<Integer> requestedPages = new ArrayList<>();
    startServer(
        exchange -> {
          int page = queryValue(exchange, "page");
          requestedPages.add(page);
          if (page == 1) {
            respond(exchange, 200, "[%s,%s]".formatted(pull(10, "octocat", true), pull(11, "other", true)));
            return;
          }
          if (page == 2) {
            respond(exchange, 200, "[%s]".formatted(pull(12, "octocat", true)));
            return;
          }
          respond(exchange, 200, "[]");
        });

    GithubSyncService syncService = newSyncService(2, 10);
    AtomicInteger captures = new AtomicInteger();
    when(sourceEvidenceCaptureService.capture(eq(user), any(NormalizedEvidence.class)))
        .thenAnswer(
            invocation -> {
              NormalizedEvidence normalized = invocation.getArgument(1);
              int number = pullNumber(normalized.externalId());
              return
                  new EvidenceCaptureResult(
                      response(number), captures.getAndIncrement() < 2);
            });

    var first = syncService.sync(user);
    var second = syncService.sync(user);

    assertThat(first.discovered()).isEqualTo(2);
    assertThat(first.created()).isEqualTo(2);
    assertThat(first.existing()).isZero();
    assertThat(first.failed()).isZero();
    assertThat(first.lastSyncOutcome()).isEqualTo("SUCCESS");
    assertThat(second.discovered()).isEqualTo(2);
    assertThat(second.created()).isZero();
    assertThat(second.existing()).isEqualTo(2);
    assertThat(second.failed()).isZero();
    assertThat(requestedPages).containsExactly(1, 2, 1, 2);
    verify(settingsService, org.mockito.Mockito.times(2))
        .recordSyncOutcome(eq(user), eq(NOW), eq("SUCCESS"));
  }

  @Test
  void countsMalformedItemsWithoutDiscardingValidCaptures() throws Exception {
    startServer(
        exchange ->
            respond(
                exchange,
                200,
                "[%s,{\"number\":\"not-a-number\"}]".formatted(pull(20, "octocat", true))));

    GithubSyncService syncService = newSyncService(2, 1);
    when(sourceEvidenceCaptureService.capture(eq(user), any(NormalizedEvidence.class)))
        .thenReturn(new EvidenceCaptureResult(response(20), true));

    var result = syncService.sync(user);

    assertThat(result.discovered()).isEqualTo(1);
    assertThat(result.created()).isEqualTo(1);
    assertThat(result.failed()).isEqualTo(1);
    assertThat(result.lastSyncOutcome()).isEqualTo("PARTIAL");
  }

  @Test
  void isolatesOneCaptureFailureAndContinuesWithTheOtherNormalizedItems() throws Exception {
    startServer(
        exchange ->
            respond(
                exchange,
                200,
                "[%s,%s]".formatted(pull(30, "octocat", true), pull(31, "octocat", true))));
    when(evidenceService.findByNaturalKey(any(), eq("GitHub"), any(String.class)))
        .thenReturn(java.util.Optional.empty());
    when(sourceEvidenceCaptureService.capture(eq(user), any(NormalizedEvidence.class)))
        .thenAnswer(
            invocation -> {
              NormalizedEvidence normalized = invocation.getArgument(1);
              if (normalized.externalId().endsWith("#31")) {
                throw new IllegalStateException("one item failed");
              }
              return new EvidenceCaptureResult(response(30), true);
            });

    var result = newSyncService(3, 10).sync(user);

    assertThat(result.discovered()).isEqualTo(2);
    assertThat(result.created()).isEqualTo(1);
    assertThat(result.existing()).isZero();
    assertThat(result.failed()).isEqualTo(1);
    assertThat(result.lastSyncOutcome()).isEqualTo("PARTIAL");
  }

  @Test
  void mapsMalformedSuccessfulPayloadToSafeGatewayFailure() throws Exception {
    startServer(exchange -> respond(exchange, 200, "{\"items\":[]}"));
    GithubSyncService syncService = newSyncService(2, 1);

    assertThatThrownBy(() -> syncService.sync(user))
        .isInstanceOf(GithubPayloadException.class)
        .hasMessage("GitHub returned an invalid pull request list");
    verify(settingsService).recordSyncOutcome(eq(user), eq(NOW), eq("FAILED"));
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403, 404, 429})
  void mapsUpstreamAccessAndRateLimitStatusesWithoutReturningTheServerToken(int status)
      throws Exception {
    String secret = "server-secret-that-must-not-leak";
    startServer(exchange -> respond(exchange, status, "{\"message\":\"upstream\"}"));
    GithubApiClient client = new GithubApiClient(new ObjectMapper(), baseUrl(), secret);
    GithubSyncService syncService =
        new GithubSyncService(
            settingsService,
            new GithubSourceAdapter(new GithubPullRequestService(client)),
            sourceEvidenceCaptureService,
            evidenceService,
            Clock.fixed(NOW, ZoneOffset.UTC),
            30,
            2,
            10);

    assertThatThrownBy(() -> syncService.sync(user))
        .isInstanceOf(GithubApiException.class)
        .satisfies(
            error -> {
              GithubApiException githubError = (GithubApiException) error;
              assertThat(githubError.statusCode()).isEqualTo(status);
              assertThat(githubError.getMessage()).doesNotContain(secret);
            });
    verify(settingsService).recordSyncOutcome(eq(user), eq(NOW), eq("FAILED_UPSTREAM_" + status));
  }

  private GithubSyncService newSyncService(int pageSize, int maxPages) {
    return
        new GithubSyncService(
            settingsService,
            new GithubSourceAdapter(
                new GithubPullRequestService(
                    new GithubApiClient(new ObjectMapper(), baseUrl(), "stub-secret"))),
            sourceEvidenceCaptureService,
            evidenceService,
            Clock.fixed(NOW, ZoneOffset.UTC),
            30,
            pageSize,
            maxPages);
  }

  private void startServer(HttpHandler handler) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/repos/acme/project/pulls", handler);
    server.start();
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  private String pull(int number, String author, boolean merged) {
    String mergedAt = merged ? "\"2026-08-08T10:00:00Z\"" : "null";
    return
        "{"
            + "\"number\":"
            + number
            + ",\"title\":\"Improve service "
            + number
            + "\",\"state\":\"closed\",\"merged_at\":"
            + mergedAt
            + ",\"closed_at\":\"2026-08-08T10:00:00Z\","
            + "\"html_url\":\"https://github.com/acme/project/pull/"
            + number
            + "\",\"user\":{\"login\":\""
            + author
            + "\"},\"updated_at\":\"2026-08-08T10:00:00Z\","
            + "\"created_at\":\"2026-08-07T10:00:00Z\",\"body\":\"Added tests\""
            + "}";
  }

  private EvidenceResponse response(int number) {
    Instant capturedAt = Instant.parse("2026-08-09T11:00:00Z");
    return
        new EvidenceResponse(
            (long) number,
            "GitHub",
            "github:acme/project#" + number,
            "PR #" + number + " - acme/project",
            "Added tests",
            "https://github.com/acme/project/pull/" + number,
            capturedAt,
            capturedAt,
            EvidenceStatus.PENDING);
  }

  private int pullNumber(String externalId) {
    return Integer.parseInt(externalId.substring(externalId.indexOf('#') + 1));
  }

  private int queryValue(HttpExchange exchange, String name) {
    String query = exchange.getRequestURI().getQuery();
    for (String part : query == null ? new String[0] : query.split("&")) {
      String[] pair = part.split("=", 2);
      if (pair.length == 2 && pair[0].equals(name)) {
        return Integer.parseInt(pair[1]);
      }
    }
    return 0;
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
