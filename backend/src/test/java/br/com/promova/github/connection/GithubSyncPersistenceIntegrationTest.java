package br.com.promova.github.connection;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.promova.evidence.EvidenceRepository;
import br.com.promova.github.connection.dto.GithubSettingsRequest;
import br.com.promova.user.User;
import br.com.promova.user.UserRepository;
import br.com.promova.user.UserRole;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    properties = {
      "github.api.token=server-secret",
      "github.sync.lookback-days=30",
      "github.sync.page-size=2",
      "github.sync.max-pages=10"
    })
@ActiveProfiles("test")
class GithubSyncPersistenceIntegrationTest {
  private static final String SERVER_SECRET = "server-secret";
  private static final HttpServer SERVER = createServer();
  private static final List<Integer> REQUESTED_PAGES = new ArrayList<>();
  private static final AtomicInteger USERS = new AtomicInteger();

  @Autowired private GithubConnectionSettingsService settingsService;
  @Autowired private GithubSyncService syncService;
  @Autowired private UserRepository userRepository;
  @Autowired private EvidenceRepository evidenceRepository;

  @DynamicPropertySource
  static void githubProperties(DynamicPropertyRegistry registry) {
    registry.add("github.api.base-url", () -> baseUrl());
  }

  @BeforeEach
  void clearRequests() {
    REQUESTED_PAGES.clear();
  }

  @AfterAll
  static void stopServer() {
    SERVER.stop(0);
  }

  @Test
  void persistsThreePendingRowsThenReportsAllThreeAsExistingOnRepeatSync() {
    int suffix = USERS.incrementAndGet();
    User user =
        userRepository.save(
            new User(
                "Sync Owner " + suffix,
                "sync-owner-" + suffix + "@example.com",
                "hash",
                UserRole.EMPLOYEE));
    settingsService.updateForUser(user, new GithubSettingsRequest("acme/project", "octocat"));

    var first = syncService.sync(user);
    var second = syncService.sync(user);

    assertThat(first.discovered()).isEqualTo(3);
    assertThat(first.created()).isEqualTo(3);
    assertThat(first.existing()).isZero();
    assertThat(first.failed()).isZero();
    assertThat(second.discovered()).isEqualTo(3);
    assertThat(second.created()).isZero();
    assertThat(second.existing()).isEqualTo(3);
    assertThat(second.failed()).isZero();
    var persisted = evidenceRepository.findForUser(user.getId(), null, null, null);
    assertThat(persisted).hasSize(3);
    assertThat(persisted)
        .extracting(evidence -> evidence.getExternalId())
        .containsExactlyInAnyOrder(
            "github:acme/project#100", "github:acme/project#101", "github:acme/project#102");
    assertThat(persisted)
        .allSatisfy(
            evidence -> {
              assertThat(evidence.getSource()).isEqualTo("GitHub");
              assertThat(evidence.getSourceMeta()).contains("acme/project");
              assertThat(evidence.getContent()).contains("PR #");
              assertThat(evidence.getSourceUrl()).startsWith("https://github.com/");
              assertThat(evidence.getSourceMeta()).doesNotContain(SERVER_SECRET);
              assertThat(evidence.getContent()).doesNotContain(SERVER_SECRET);
              assertThat(evidence.getSourceUrl()).doesNotContain(SERVER_SECRET);
            });
    assertThat(first.toString()).doesNotContain(SERVER_SECRET);
    assertThat(REQUESTED_PAGES).containsExactly(1, 2, 1, 2);

    var savedSettings = settingsService.getForUser(user);
    assertThat(savedSettings.lastSyncOutcome()).isEqualTo("SUCCESS");
    assertThat(savedSettings.lastSyncAt()).isNotNull();
  }

  private static HttpServer createServer() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/repos/acme/project/pulls",
          GithubSyncPersistenceIntegrationTest::handlePulls);
      server.start();
      return server;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private static void handlePulls(HttpExchange exchange) throws IOException {
    int page = queryValue(exchange, "page");
    REQUESTED_PAGES.add(page);
    String body =
        switch (page) {
          case 1 -> "[%s,%s]".formatted(pull(100), pull(101));
          case 2 -> "[%s]".formatted(pull(102));
          default -> "[]";
        };
    respond(exchange, 200, body);
  }

  private static String pull(int number) {
    return
        "{"
            + "\"number\":"
            + number
            + ",\"title\":\"Improve service "
            + number
            + "\",\"state\":\"closed\",\"merged_at\":\"2026-08-08T10:00:00Z\","
            + "\"closed_at\":\"2026-08-08T10:00:00Z\",\"html_url\":\"https://github.com/acme/project/pull/"
            + number
            + "\",\"user\":{\"login\":\"octocat\"},\"updated_at\":\"2026-08-08T10:00:00Z\","
            + "\"created_at\":\"2026-08-07T10:00:00Z\",\"body\":\"Added tests "
            + SERVER_SECRET
            + "\""
            + "}";
  }

  private static int queryValue(HttpExchange exchange, String name) {
    String query = exchange.getRequestURI().getQuery();
    for (String part : query == null ? new String[0] : query.split("&")) {
      String[] pair = part.split("=", 2);
      if (pair.length == 2 && pair[0].equals(name)) {
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

  private static String baseUrl() {
    return "http://127.0.0.1:" + SERVER.getAddress().getPort();
  }
}
