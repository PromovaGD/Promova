package br.com.promova.analysis.review.controller;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.promova.analysis.dto.SavedAnalysisRequest;
import br.com.promova.analysis.dto.SavedAnalysisResponse;
import br.com.promova.analysis.service.SavedAnalysisService;
import br.com.promova.user.User;
import br.com.promova.user.UserRepository;
import br.com.promova.user.UserRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AnalysisReviewHttpSmokeIntegrationTest {
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private SavedAnalysisService savedAnalysisService;
  @LocalServerPort private int applicationPort;

  @Test
  void coversAuthenticatedReviewLifecycleOwnershipAndAppendOnlyHistory() throws Exception {
    String employeeEmail = uniqueEmail("employee");
    String employeeToken = registerToken(employeeEmail);
    User employee = userRepository.findByEmailIgnoreCase(employeeEmail).orElseThrow();

    String otherEmployeeEmail = uniqueEmail("other");
    String otherEmployeeToken = registerToken(otherEmployeeEmail);
    User otherEmployee = userRepository.findByEmailIgnoreCase(otherEmployeeEmail).orElseThrow();

    String managerEmail = uniqueEmail("manager");
    String managerToken = registerToken(managerEmail);
    User manager = userRepository.findByEmailIgnoreCase(managerEmail).orElseThrow();
    manager.setRole(UserRole.MANAGER);
    userRepository.save(manager);

    SavedAnalysisResponse employeeAnalysis = saveAnalysis(employee, "reviewed-analysis");
    SavedAnalysisResponse otherAnalysis = saveAnalysis(otherEmployee, "other-analysis");
    Long analysisId = employeeAnalysis.analysisId();
    Long otherAnalysisId = otherAnalysis.analysisId();
    assertThat(analysisId).isNotNull();
    assertThat(otherAnalysisId).isNotNull();

    JsonNode analyses = json(request("GET", "/analyses", employeeToken, null));
    assertThat(analyses).hasSize(1);
    assertThat(analyses.get(0).path("analysisId").asLong()).isEqualTo(analysisId);

    HttpResponse<String> anonymousRead =
        request("GET", reviewPath(analysisId), null, null);
    assertThat(anonymousRead.statusCode()).isEqualTo(401);

    JsonNode initial = json(request("GET", reviewPath(analysisId), employeeToken, null));
    assertThat(initial.path("currentStatus").asText()).isEqualTo("UNREVIEWED");
    assertThat(initial.path("history")).isEmpty();

    HttpResponse<String> anonymousWrite =
        request("POST", reviewPath(analysisId), null, "{\"status\":\"ACCEPTED\"}");
    assertThat(anonymousWrite.statusCode()).isEqualTo(401);

    HttpResponse<String> employeeWrite =
        request(
            "POST",
            reviewPath(analysisId),
            employeeToken,
            "{\"status\":\"ACCEPTED\",\"reviewerId\":999}");
    assertThat(employeeWrite.statusCode()).isEqualTo(403);

    JsonNode managerInitial =
        json(
            request(
                "GET",
                managerReviewPath(employee.getId(), analysisId),
                managerToken,
                null));
    assertThat(managerInitial.path("currentStatus").asText()).isEqualTo("UNREVIEWED");

    Instant beforeFirstReview = Instant.now();
    JsonNode accepted =
        json(
            requireCreated(
                request(
                    "POST",
                    managerReviewPath(employee.getId(), analysisId),
                    managerToken,
                    "{\"status\":\"ACCEPTED\",\"comment\":\"Clear evidence.\","
                        + "\"reviewerId\":999,\"employeeId\":999,"
                        + "\"createdAt\":\"2099-01-01T00:00:00Z\","
                        + "\"updatedAt\":\"2099-01-01T00:00:00Z\"}")));
    assertThat(accepted.path("currentStatus").asText()).isEqualTo("ACCEPTED");
    assertThat(accepted.path("history")).hasSize(1);
    assertThat(accepted.path("history").get(0).path("reviewerId").asLong())
        .isEqualTo(manager.getId());
    assertThat(accepted.path("history").get(0).path("reviewerEmail").asText())
        .isEqualTo(managerEmail);
    Instant firstCreatedAt =
        Instant.parse(accepted.path("history").get(0).path("createdAt").asText());
    assertThat(firstCreatedAt).isAfterOrEqualTo(beforeFirstReview);
    assertThat(firstCreatedAt).isBefore(Instant.now().plusSeconds(2));
    assertThat(firstCreatedAt.toString()).doesNotStartWith("2099-");

    JsonNode needsContext =
        json(
            requireCreated(
                request(
                    "POST",
                    managerReviewPath(employee.getId(), analysisId),
                    managerToken,
                    "{\"status\":\"NEEDS_CONTEXT\",\"comment\":\"Add outcome metrics.\"}")));
    assertThat(needsContext.path("currentStatus").asText()).isEqualTo("NEEDS_CONTEXT");
    assertThat(needsContext.path("history")).hasSize(2);
    assertThat(needsContext.path("history").get(0).path("status").asText())
        .isEqualTo("ACCEPTED");
    assertThat(needsContext.path("history").get(1).path("status").asText())
        .isEqualTo("NEEDS_CONTEXT");
    assertThat(needsContext.path("history").get(0).path("id").asLong())
        .isLessThan(needsContext.path("history").get(1).path("id").asLong());

    JsonNode employeeHistory = json(request("GET", reviewPath(analysisId), employeeToken, null));
    assertThat(employeeHistory.path("currentStatus").asText()).isEqualTo("NEEDS_CONTEXT");
    assertThat(employeeHistory.path("history")).hasSize(2);
    assertThat(employeeHistory.path("history").get(0).path("comment").asText())
        .isEqualTo("Clear evidence.");
    assertThat(employeeHistory.path("history").get(1).path("comment").asText())
        .isEqualTo("Add outcome metrics.");

    HttpResponse<String> unknownStatus =
        request(
            "POST",
            managerReviewPath(employee.getId(), analysisId),
            managerToken,
            "{\"status\":\"UNKNOWN\"}");
    assertThat(unknownStatus.statusCode()).isEqualTo(400);

    HttpResponse<String> oversizedComment =
        request(
            "POST",
            managerReviewPath(employee.getId(), analysisId),
            managerToken,
            "{\"status\":\"ACCEPTED\",\"comment\":\"" + "x".repeat(2001) + "\"}");
    assertThat(oversizedComment.statusCode()).isEqualTo(400);

    HttpResponse<String> wrongOwnerReviewRead =
        request(
            "GET",
            managerReviewPath(employee.getId(), otherAnalysisId),
            managerToken,
            null);
    assertThat(wrongOwnerReviewRead.statusCode()).isEqualTo(404);

    HttpResponse<String> employeeCrossRecordRead =
        request("GET", reviewPath(otherAnalysisId), employeeToken, null);
    assertThat(employeeCrossRecordRead.statusCode()).isEqualTo(404);

    // Keep the second authenticated employee active so this flow also proves another owner exists.
    assertThat(otherEmployeeToken).isNotBlank();
  }

  private SavedAnalysisResponse saveAnalysis(User owner, String externalId) {
    return savedAnalysisService.save(
        owner,
        new SavedAnalysisRequest(
            externalId,
            "GitHub",
            "PR #7 - promova/app",
            "Refactored checkout and added tests",
            "L3",
            "L4",
            "L4",
            "high",
            "Server-owned classification",
            "Aligned with target",
            Instant.parse("2026-08-01T10:00:00Z"),
            List.of("Ownership"),
            List.of("Add measurable outcomes")));
  }

  private String registerToken(String email) throws Exception {
    JsonNode response =
        json(
            request(
                "POST",
                "/auth/register",
                null,
                "{\"name\":\"HTTP Review User\",\"email\":\""
                    + email
                    + "\",\"password\":\"senha123\"}"));
    return response.path("token").asText();
  }

  private HttpResponse<String> requireCreated(HttpResponse<String> response) {
    assertThat(response.statusCode()).isEqualTo(201);
    return response;
  }

  private JsonNode json(HttpResponse<String> response) throws IOException {
    assertThat(response.statusCode()).isLessThan(300);
    return objectMapper.readTree(response.body());
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

  private String reviewPath(Long analysisId) {
    return "/analyses/" + analysisId + "/reviews";
  }

  private String managerReviewPath(Long employeeId, Long analysisId) {
    return "/manager/employees/" + employeeId + "/analyses/" + analysisId + "/reviews";
  }

  private String applicationBaseUrl() {
    return "http://127.0.0.1:" + applicationPort;
  }

  private String uniqueEmail(String prefix) {
    return prefix + "-" + UUID.randomUUID() + "@example.com";
  }
}
