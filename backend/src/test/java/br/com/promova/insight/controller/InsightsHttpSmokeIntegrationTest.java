package br.com.promova.insight.controller;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.promova.analysis.dto.SavedAnalysisRequest;
import br.com.promova.analysis.persistence.SavedAnalysis;
import br.com.promova.analysis.persistence.SavedAnalysisRepository;
import br.com.promova.analysis.service.SavedAnalysisService;
import br.com.promova.evidence.Evidence;
import br.com.promova.evidence.EvidenceRepository;
import br.com.promova.user.User;
import br.com.promova.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InsightsHttpSmokeIntegrationTest {
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  private static final Path FRAMEWORK_FILE = createPartialFrameworkFile();

  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private EvidenceRepository evidenceRepository;
  @Autowired private SavedAnalysisRepository savedAnalysisRepository;
  @Autowired private SavedAnalysisService savedAnalysisService;
  @LocalServerPort private int applicationPort;

  @DynamicPropertySource
  static void frameworkProperties(DynamicPropertyRegistry registry) {
    registry.add("promova.framework.path", () -> FRAMEWORK_FILE.toUri().toString());
  }

  @AfterAll
  static void removeTemporaryFramework() throws IOException {
    Files.deleteIfExists(FRAMEWORK_FILE);
  }

  @Test
  void coversEmptyPopulatedFilteredOwnershipAndPartialFrameworkOverHttp() throws Exception {
    assertThat(request("GET", "/insights", null, null).statusCode()).isEqualTo(401);

    String emptyToken = registerToken("empty");
    JsonNode empty = json(request("GET", "/insights", emptyToken, null));
    assertThat(empty.path("totalEvidence").asInt()).isZero();
    assertThat(empty.path("sourceDistribution")).isEmpty();
    assertThat(empty.path("recentTrend")).isEmpty();
    assertThat(empty.path("criterionCoverage")).hasSize(1);
    assertThat(empty.path("criterionCoverage").get(0).path("status").asText())
        .isEqualTo("NO_EVIDENCE");

    String ownerEmail = "owner-" + UUID.randomUUID() + "@example.com";
    String ownerToken = registerToken(ownerEmail);
    User owner = userRepository.findByEmailIgnoreCase(ownerEmail).orElseThrow();
    saveTrustedAnalysis(
        owner,
        "http-github",
        "GitHub",
        "PR #10",
        "L10",
        List.of("Testing"),
        Instant.parse("2026-05-12T10:00:00Z"));
    saveAnalysis(
        owner,
        "http-legacy",
        "GitHub",
        "PR #legacy",
        "L10",
        List.of("Testing"),
        Instant.parse("2026-05-11T10:00:00Z"));
    saveAnalysis(
        owner,
        "http-jira",
        "Jira",
        "PROM-10",
        "L2",
        List.of("Unmapped competency"),
        Instant.parse("2026-05-10T10:00:00Z"));
    saveAnalysis(
        owner,
        "http-outside-window",
        "Slack",
        "#old",
        "L10",
        List.of("Testing"),
        Instant.parse("2026-04-30T10:00:00Z"));

    JsonNode populated =
        json(
            request(
                "GET",
                "/insights?from=2026-05-01T00:00:00Z&to=2026-05-31T23:59:59.999Z",
                ownerToken,
                null));
    assertThat(populated.path("totalEvidence").asInt()).isEqualTo(3);
    assertThat(populated.path("sourceDistribution")).hasSize(2);
    assertThat(populated.path("estimatedLevelDistribution")).hasSize(2);
    assertThat(populated.path("criterionCoverage")).hasSize(1);
    assertThat(populated.path("criterionCoverage").get(0).path("status").asText())
        .isEqualTo("SUPPORTED");
    JsonNode supportingEvidence = populated.path("criterionCoverage").get(0).path("supportingEvidence");
    assertThat(supportingEvidence).hasSize(2);
    assertThat(supportingEvidence.get(0).path("id").asText()).isEqualTo("http-github");
    assertThat(supportingEvidence.get(0).path("evidenceId").asLong()).isPositive();
    assertThat(supportingEvidence.get(1).path("id").asText()).isEqualTo("http-legacy");
    assertThat(supportingEvidence.get(1).path("evidenceId").isNull()).isTrue();
    assertThat(populated.path("gaps")).isEmpty();
    assertThat(populated.path("recentTrend")).isNotEmpty();
    assertThat(populated.toString()).doesNotContain("http-outside-window");

    String otherToken = registerToken("other");
    JsonNode other =
        json(request("GET", "/insights?userId=" + owner.getId(), otherToken, null));
    assertThat(other.path("totalEvidence").asInt()).isZero();
    assertThat(other.path("sourceDistribution")).isEmpty();
  }

  private void saveAnalysis(
      User owner,
      String externalId,
      String source,
      String sourceMeta,
      String impactLevel,
      List<String> competencies,
      Instant createdAt) {
    savedAnalysisService.save(
        owner,
        new SavedAnalysisRequest(
            externalId,
            source,
            sourceMeta,
            "Saved evidence for HTTP smoke",
            "L2",
            "L10",
            impactLevel,
            "high",
            "Fixed test justification",
            "Fixed test readiness",
            createdAt,
            competencies,
            List.of("Suggestion")));
  }

  private void saveTrustedAnalysis(
      User owner,
      String externalId,
      String source,
      String sourceMeta,
      String impactLevel,
      List<String> competencies,
      Instant createdAt)
      throws Exception {
    Evidence evidence =
        evidenceRepository.save(
            new Evidence(
                owner,
                source,
                externalId,
                sourceMeta,
                "Trusted evidence for HTTP smoke",
                "https://example.test/" + externalId,
                createdAt));
    savedAnalysisRepository.save(
        new SavedAnalysis(
            evidence,
            "L2",
            "L10",
            impactLevel,
            "high",
            "Fixed test justification",
            objectMapper.writeValueAsString(competencies),
            objectMapper.writeValueAsString(List.of("Suggestion")),
            "Fixed test readiness",
            createdAt));
  }

  private String registerToken(String nameOrEmailPart) throws Exception {
    String email =
        nameOrEmailPart.contains("@")
            ? nameOrEmailPart
            : nameOrEmailPart + "-" + UUID.randomUUID() + "@example.com";
    JsonNode response =
        json(
            request(
                "POST",
                "/auth/register",
                null,
                "{\"name\":\"HTTP Smoke\",\"email\":\""
                    + email
                    + "\",\"password\":\"senha123\"}"));
    return response.path("token").asText();
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
    assertThat(response.statusCode()).isLessThan(300);
    return objectMapper.readTree(response.body());
  }

  private String applicationBaseUrl() {
    return "http://127.0.0.1:" + applicationPort;
  }

  private static Path createPartialFrameworkFile() {
    try {
      Path file = Files.createTempFile("promova-insights-framework-", ".json");
      Files.writeString(
          file,
          """
          {
            "levels": {
              "L2": {
                "title": "Engineer I",
                "description": "Engineer I",
                "criteria": {}
              },
              "L10": {
                "title": "Engineer II",
                "description": "Engineer II",
                "criteria": {
                  "Testing": "Writes tests for expected behavior."
                }
              }
            }
          }
          """);
      return file;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }
}
