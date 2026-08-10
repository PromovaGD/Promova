package br.com.promova.github.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.config.ApiExceptionHandler;
import br.com.promova.config.WebConfig;
import br.com.promova.github.service.GithubPullRequestService;
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

@WebMvcTest(GithubIntegrationController.class)
@Import({WebConfig.class, ApiExceptionHandler.class})
class GithubIntegrationControllerSecurityTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private GithubPullRequestService githubPullRequestService;
  @MockitoBean private AuthService authService;
  @MockitoBean private AuthTokenResolver authTokenResolver;

  @Test
  void rejectsAnonymousGithubExtraction() throws Exception {
    mockMvc
        .perform(get("/api/github/repos/acme/project/pulls"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void allowsAuthenticatedGithubExtraction() throws Exception {
    User employee = new User("Employee", "employee@example.com", "hash", UserRole.EMPLOYEE);
    when(authTokenResolver.resolve("Bearer employee-token")).thenReturn("employee-token");
    when(authService.requireUser("employee-token")).thenReturn(employee);
    when(githubPullRequestService.listPullRequests("acme", "project", "open", 10, 1))
        .thenReturn(List.of());

    mockMvc
        .perform(
            get("/api/github/repos/acme/project/pulls")
                .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
        .andExpect(status().isOk());
  }
}
