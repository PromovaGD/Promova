package br.com.promova.evidence.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.evidence.service.CapturedEvidenceService;
import br.com.promova.evidence.service.GithubCapturedEvidenceService;
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

@WebMvcTest(CapturedEvidenceController.class)
@Import(CapturedEvidenceService.class)
class CapturedEvidenceControllerTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthService authService;
  @MockitoBean private AuthTokenResolver authTokenResolver;
  @MockitoBean private GithubCapturedEvidenceService githubCapturedEvidenceService;

  @BeforeEach
  void authenticateRequests() {
    User employee = new User("Employee", "employee@example.com", "hash", UserRole.EMPLOYEE);
    when(authTokenResolver.resolve("Bearer test-token")).thenReturn("test-token");
    when(authService.requireUser("test-token")).thenReturn(employee);
  }

  @Test
  void returnsNextCapturedEvidence() throws Exception {
    mockMvc
        .perform(
            get("/evidences/next")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .param("cursor", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("jira-prom-218"))
        .andExpect(jsonPath("$.source").value("Jira"))
        .andExpect(jsonPath("$.nextCursor").value(2));
  }
}
