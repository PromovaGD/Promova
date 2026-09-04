package br.com.promova.profile.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.config.ApiExceptionHandler;
import br.com.promova.config.WebConfig;
import br.com.promova.profile.ProfileService;
import br.com.promova.profile.dto.FrameworkLevelResponse;
import br.com.promova.profile.dto.ProfileResponse;
import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProfileController.class)
@Import({WebConfig.class, ApiExceptionHandler.class})
class ProfileControllerSecurityTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthService authService;
  @MockitoBean private AuthTokenResolver authTokenResolver;
  @MockitoBean private ProfileService profileService;

  private User employee;
  private ProfileResponse response;

  @BeforeEach
  void setUp() {
    employee = new User("Employee", "employee@example.com", "hash", UserRole.EMPLOYEE);
    response =
        new ProfileResponse(
            "L3",
            "L4",
            List.of(
                new FrameworkLevelResponse("L3", "Software Engineer I"),
                new FrameworkLevelResponse("L4", "Software Engineer II")));
    when(authTokenResolver.resolve("Bearer employee-token")).thenReturn("employee-token");
    when(authService.requireUser("employee-token")).thenReturn(employee);
  }

  @Test
  void rejectsAnonymousProfileLookup() throws Exception {
    mockMvc
        .perform(get("/profile"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Autenticação necessária."));
  }

  @Test
  void returnsAuthenticatedUsersProfileAndFrameworkLevels() throws Exception {
    when(profileService.getProfile(employee)).thenReturn(response);

    mockMvc
        .perform(get("/profile").header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentLevel").value("L3"))
        .andExpect(jsonPath("$.targetLevel").value("L4"))
        .andExpect(jsonPath("$.levels[0].key").value("L3"))
        .andExpect(jsonPath("$.levels[0].title").value("Software Engineer I"));
  }

  @Test
  void refusesEmployeeCareerPlanMutation() throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"currentLevel\":\"L3\",\"targetLevel\":\"L4\"}"))
        .andExpect(status().isMethodNotAllowed());
  }
}
