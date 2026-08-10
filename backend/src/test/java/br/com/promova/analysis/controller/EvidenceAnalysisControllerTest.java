package br.com.promova.analysis.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

import br.com.promova.analysis.engine.MockAnalysisEngine;
import br.com.promova.analysis.service.EvidenceAnalysisService;
import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.framework.JsonFrameworkProvider;
import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EvidenceAnalysisController.class)
@Import({EvidenceAnalysisService.class, MockAnalysisEngine.class, JsonFrameworkProvider.class})
class EvidenceAnalysisControllerTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthService authService;
  @MockitoBean private AuthTokenResolver authTokenResolver;

  @BeforeEach
  void authenticateRequests() {
    User employee = new User("Employee", "employee@example.com", "hash", UserRole.EMPLOYEE);
    when(authTokenResolver.resolve("Bearer test-token")).thenReturn("test-token");
    when(authService.requireUser("test-token")).thenReturn(employee);
  }

  @Test
  void analyzesEvidence() throws Exception {
    mockMvc
        .perform(
            post("/analyze")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "evidence": "Refactored payment module and increased test coverage",
                      "currentLevel": "L3",
                      "targetLevel": "L4"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estimatedLevel").value("L4"))
        .andExpect(jsonPath("$.confidence").value("medium"))
        .andExpect(jsonPath("$.competencies", containsInAnyOrder("Code Quality", "Ownership")));
  }

  @Test
  void rejectsBlankEvidence() throws Exception {
    mockMvc
        .perform(
            post("/analyze")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "evidence": "",
                      "currentLevel": "L3",
                      "targetLevel": "L4"
                    }
                    """))
        .andExpect(status().isBadRequest());
  }
}
