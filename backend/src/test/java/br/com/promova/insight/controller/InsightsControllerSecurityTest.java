package br.com.promova.insight.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.config.ApiExceptionHandler;
import br.com.promova.config.WebConfig;
import br.com.promova.insight.dto.InsightsResponse;
import br.com.promova.insight.service.InsightsService;
import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InsightsController.class)
@Import({WebConfig.class, ApiExceptionHandler.class})
class InsightsControllerSecurityTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private InsightsService insightsService;
  @MockitoBean private AuthService authService;
  @MockitoBean private AuthTokenResolver authTokenResolver;

  @Test
  void rejectsAnonymousInsightAccess() throws Exception {
    mockMvc
        .perform(get("/insights"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Token ausente."));
  }

  @Test
  void usesTheAuthenticatedUserAndInclusiveDateBounds() throws Exception {
    User employee = user(7L, "employee@example.com");
    Instant from = Instant.parse("2026-05-01T00:00:00Z");
    Instant to = Instant.parse("2026-05-31T23:59:59.999Z");
    when(authTokenResolver.resolve("Bearer employee-token")).thenReturn("employee-token");
    when(authService.requireUser("employee-token")).thenReturn(employee);
    when(insightsService.summarizeForUser(employee, from, to)).thenReturn(emptyResponse());

    mockMvc
        .perform(
            get("/insights")
                .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                .param("from", from.toString())
                .param("to", to.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalEvidence").value(0));

    verify(insightsService).summarizeForUser(employee, from, to);
  }

  @Test
  void doesNotUseAClientSuppliedEmployeeIdentifier() throws Exception {
    User employee = user(7L, "employee@example.com");
    when(authTokenResolver.resolve("Bearer employee-token")).thenReturn("employee-token");
    when(authService.requireUser("employee-token")).thenReturn(employee);
    when(insightsService.summarizeForUser(employee, null, null)).thenReturn(emptyResponse());

    mockMvc
        .perform(
            get("/insights")
                .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                .param("userId", "999"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalEvidence").value(0));

    verify(insightsService).summarizeForUser(employee, null, null);
  }

  private InsightsResponse emptyResponse() {
    return new InsightsResponse(0, 0, 0, List.of(), List.of(), List.of(), List.of(), List.of());
  }

  private User user(Long id, String email) {
    User user = new User("Employee", email, "hash", UserRole.EMPLOYEE);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }
}
