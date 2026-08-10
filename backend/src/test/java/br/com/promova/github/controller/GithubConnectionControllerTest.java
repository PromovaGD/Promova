package br.com.promova.github.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.config.ApiExceptionHandler;
import br.com.promova.config.WebConfig;
import br.com.promova.github.connection.GithubConnectionSettingsService;
import br.com.promova.github.connection.GithubSyncService;
import br.com.promova.github.connection.dto.GithubConnectionTestResponse;
import br.com.promova.github.connection.dto.GithubSettingsRequest;
import br.com.promova.github.connection.dto.GithubSettingsResponse;
import br.com.promova.github.connection.dto.GithubSyncResponse;
import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import br.com.promova.github.support.GithubApiException;
import com.fasterxml.jackson.databind.node.TextNode;

@WebMvcTest(GithubConnectionController.class)
@Import({WebConfig.class, ApiExceptionHandler.class})
class GithubConnectionControllerTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthService authService;
  @MockitoBean private AuthTokenResolver authTokenResolver;
  @MockitoBean private GithubConnectionSettingsService settingsService;
  @MockitoBean private GithubSyncService syncService;
  @MockitoBean private GithubConnectionTestService connectionTestService;

  private User employee;

  @BeforeEach
  void authenticateRequests() {
    employee = new User("Employee", "employee@example.com", "hash", UserRole.EMPLOYEE);
    when(authTokenResolver.resolve("Bearer test-token")).thenReturn("test-token");
    when(authService.requireUser("test-token")).thenReturn(employee);
  }

  @Test
  void rejectsAnonymousSettingsAccess() throws Exception {
    mockMvc.perform(get("/api/github/settings")).andExpect(status().isUnauthorized());
  }

  @Test
  void updatesAndReadsOnlyTheAuthenticatedUsersSettingsContract() throws Exception {
    GithubSettingsResponse response =
        new GithubSettingsResponse(false, "acme/project", "octocat", null, "NOT_CONFIGURED");
    when(settingsService.updateForUser(
            employee, new GithubSettingsRequest("acme/project", "octocat")))
        .thenReturn(response);
    when(settingsService.getForUser(employee)).thenReturn(response);

    mockMvc
        .perform(
            put("/api/github/settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repoSlug\":\"acme/project\",\"authorLogin\":\"octocat\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.repoSlug").value("acme/project"))
        .andExpect(jsonPath("$.authorLogin").value("octocat"));

    mockMvc
        .perform(get("/api/github/settings").header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.configured").value(false))
        .andExpect(jsonPath("$.lastSyncOutcome").value("NOT_CONFIGURED"));
  }

  @Test
  void testsSavedSettingsAndReturnsSyncCounts() throws Exception {
    when(connectionTestService.test(employee))
        .thenReturn(
            new GithubConnectionTestResponse(
                true,
                "acme/project",
                "octocat",
                "GitHub repository access verified with the configured server token"));
    when(syncService.sync(employee))
        .thenReturn(
            new GithubSyncResponse(
                "acme/project",
                "octocat",
                3,
                2,
                1,
                0,
                Instant.parse("2026-08-09T12:00:00Z"),
                "SUCCESS"));

    mockMvc
        .perform(
            post("/api/github/settings/test")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("server token")));

    mockMvc
        .perform(post("/api/github/sync").header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.discovered").value(3))
        .andExpect(jsonPath("$.created").value(2))
        .andExpect(jsonPath("$.existing").value(1))
        .andExpect(jsonPath("$.failed").value(0))
        .andExpect(jsonPath("$.lastSyncOutcome").value("SUCCESS"));
  }

  @Test
  void mapsGithubFailuresWithoutReturningTheUpstreamPayloadOrToken() throws Exception {
    String secret = "server-secret";
    when(syncService.sync(employee))
        .thenThrow(new GithubApiException(403, TextNode.valueOf(secret), "raw upstream payload"));

    mockMvc
        .perform(post("/api/github/sync").header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("GitHub denied access or the server token is rate-limited."))
        .andExpect(jsonPath("$.github_response").doesNotExist())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(secret))));
  }
}
