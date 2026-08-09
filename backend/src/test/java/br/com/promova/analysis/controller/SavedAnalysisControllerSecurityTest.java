package br.com.promova.analysis.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.promova.analysis.service.SavedAnalysisService;
import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.config.ApiExceptionHandler;
import br.com.promova.config.WebConfig;
import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SavedAnalysisController.class)
@Import({WebConfig.class, ApiExceptionHandler.class})
class SavedAnalysisControllerSecurityTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private SavedAnalysisService savedAnalysisService;
  @MockitoBean private AuthService authService;
  @MockitoBean private AuthTokenResolver authTokenResolver;

  @Test
  void rejectsAnonymousSavedAnalysisAccess() throws Exception {
    mockMvc
        .perform(get("/analyses"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void listsOnlyForTheAuthenticatedUser() throws Exception {
    User employee = new User("Employee", "employee@example.com", "hash", UserRole.EMPLOYEE);
    when(authTokenResolver.resolve("Bearer employee-token")).thenReturn("employee-token");
    when(authService.requireUser("employee-token")).thenReturn(employee);
    when(savedAnalysisService.listForUser(employee, null, null)).thenReturn(List.of());

    mockMvc
        .perform(get("/analyses").header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
        .andExpect(status().isOk());

    verify(savedAnalysisService).listForUser(employee, null, null);
  }
}
