package br.com.promova.evidence.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.config.ApiExceptionHandler;
import br.com.promova.config.WebConfig;
import br.com.promova.evidence.EvidenceStatus;
import br.com.promova.evidence.dto.EvidenceResponse;
import br.com.promova.evidence.service.EvidenceService;
import br.com.promova.evidence.service.GithubCapturedEvidenceService;
import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(CapturedEvidenceController.class)
@Import({WebConfig.class, ApiExceptionHandler.class})
class CapturedEvidenceControllerTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthService authService;
  @MockitoBean private AuthTokenResolver authTokenResolver;
  @MockitoBean private EvidenceService evidenceService;
  @MockitoBean private GithubCapturedEvidenceService githubCapturedEvidenceService;

  private User employee;

  @BeforeEach
  void authenticateRequests() {
    employee = new User("Employee", "employee@example.com", "hash", UserRole.EMPLOYEE);
    when(authTokenResolver.resolve("Bearer test-token")).thenReturn("test-token");
    when(authService.requireUser("test-token")).thenReturn(employee);
  }

  @Test
  void rejectsAnonymousEvidenceList() throws Exception {
    mockMvc.perform(get("/evidences")).andExpect(status().isUnauthorized());
  }

  @Test
  void listsOnlyTheAuthenticatedUsersFilteredInbox() throws Exception {
    Instant from = Instant.parse("2026-05-01T00:00:00Z");
    Instant to = Instant.parse("2026-05-31T23:59:59Z");
    EvidenceResponse response =
        new EvidenceResponse(
            41L,
            "GitHub",
            "github:acme/project#7",
            "PR #7 - acme/project",
            "Changed the checkout service",
            "https://github.com/acme/project/pull/7",
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z"),
            EvidenceStatus.PENDING);
    when(evidenceService.listForUser(employee, "PENDING", from, to))
        .thenReturn(List.of(response));

    mockMvc
        .perform(
            get("/evidences")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .param("status", "PENDING")
                .param("from", from.toString())
                .param("to", to.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(41))
        .andExpect(jsonPath("$[0].status").value("PENDING"))
        .andExpect(jsonPath("$[0].sourceUrl").value("https://github.com/acme/project/pull/7"));
  }

  @Test
  void returnsNotFoundWhenAnotherUserOwnsTheRequestedEvidence() throws Exception {
    when(evidenceService.getForUser(eq(employee), eq(41L)))
        .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Evidência não encontrada."));

    mockMvc
        .perform(
            get("/evidences/41")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Evidência não encontrada."));
  }

  @Test
  void rejectsDismissalOfEvidenceThatIsNotPending() throws Exception {
    when(evidenceService.dismiss(eq(employee), eq(41L)))
        .thenThrow(new ResponseStatusException(
            org.springframework.http.HttpStatus.CONFLICT,
            "Evidência não pode ser dispensada neste estado."));

    mockMvc
        .perform(
            post("/evidences/41/dismiss")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
        .andExpect(status().isConflict());
  }

  @Test
  void persistsGithubCaptureForTheAuthenticatedUser() throws Exception {
    EvidenceResponse response =
        new EvidenceResponse(
            52L,
            "GitHub",
            "github:acme/project#9",
            "PR #9 - acme/project",
            "Added tests",
            "https://github.com/acme/project/pull/9",
            Instant.parse("2026-05-12T10:00:00Z"),
            Instant.parse("2026-05-12T10:00:00Z"),
            EvidenceStatus.PENDING);
    when(githubCapturedEvidenceService.fromPullRequest(employee, "acme/project", 9, "octocat"))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/evidences/github/pull-request")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"repo":"acme/project","pullNumber":9,"usernameHint":"octocat"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(52))
        .andExpect(jsonPath("$.status").value("PENDING"));

    verify(githubCapturedEvidenceService)
        .fromPullRequest(employee, "acme/project", 9, "octocat");
  }
}
