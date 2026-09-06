package br.com.promova.analysis.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.config.ApiExceptionHandler;
import br.com.promova.config.WebConfig;
import br.com.promova.evidence.service.EvidenceService;
import br.com.promova.evidence.service.GithubCapturedEvidenceService;
import br.com.promova.analysis.service.EvidenceAnalysisService;
import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(br.com.promova.evidence.controller.CapturedEvidenceController.class)
@Import({WebConfig.class, ApiExceptionHandler.class})
class EvidenceAnalysisControllerTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthService authService;
  @MockitoBean private AuthTokenResolver authTokenResolver;
  @MockitoBean private EvidenceService evidenceService;
  @MockitoBean private GithubCapturedEvidenceService githubCapturedEvidenceService;
  @MockitoBean private EvidenceAnalysisService evidenceAnalysisService;

  @BeforeEach
  void authenticateRequests() {
    User employee = new User("Employee", "employee@example.com", "hash", UserRole.EMPLOYEE);
    when(authTokenResolver.resolve("Bearer test-token")).thenReturn("test-token");
    when(authService.requireUser("test-token")).thenReturn(employee);
  }

  @Test
  void removesTheLegacyBrowserSuppliedAnalysisRoute() throws Exception {
    mockMvc
        .perform(
            post("/analyze")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .contentType("application/json")
                .content(
                    """
                    {"evidence":"client input","currentLevel":"L3","targetLevel":"L4"}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void rejectsAnOversizedEmployeeObservation() throws Exception {
    mockMvc
        .perform(
            post("/evidences/41/analysis")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .contentType("application/json")
                .content("{\"userObservation\":\"" + "x".repeat(2001) + "\"}"))
        .andExpect(status().isBadRequest());
  }
}
