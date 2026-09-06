package br.com.promova.organization;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.config.ApiExceptionHandler;
import br.com.promova.config.WebConfig;
import br.com.promova.organization.controller.ManagerSettingsController;
import br.com.promova.organization.dto.JobRoleRequest;
import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ManagerSettingsController.class)
@Import({WebConfig.class, ApiExceptionHandler.class})
class ManagerSettingsControllerTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthService authService;
  @MockitoBean private AuthTokenResolver authTokenResolver;
  @MockitoBean private OrganizationConfigurationService configurationService;

  @Test
  void employeesCannotMutateTheCatalog() throws Exception {
    authenticate(UserRole.EMPLOYEE);

    mockMvc
        .perform(
            post("/manager/settings/job-roles")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Platform","description":"Platform role","allowedLevelIds":["L3","L4"]}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void managersCanCreateAFrameworkBoundRole() throws Exception {
    authenticate(UserRole.MANAGER);

    mockMvc
        .perform(
            post("/manager/settings/job-roles")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Platform","description":"Platform role","allowedLevelIds":["L3","L4"]}
                    """))
        .andExpect(status().isOk());

    verify(configurationService)
        .createRole(new JobRoleRequest("Platform", "Platform role", List.of("L3", "L4")));
  }

  @Test
  void validatesTerminologyFields() throws Exception {
    authenticate(UserRole.MANAGER);

    mockMvc
        .perform(
            put("/manager/settings/terminology")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void returnsConflictAndAffectedCountForAnAssignedRole() throws Exception {
    authenticate(UserRole.MANAGER);
    when(configurationService.archiveRole(7L, new br.com.promova.organization.dto.JobRoleArchiveRequest(null)))
        .thenThrow(new JobRoleInUseException(3));

    mockMvc
        .perform(
            post("/manager/settings/job-roles/7/archive")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.affectedCount").value(3));
  }

  private void authenticate(UserRole role) {
    when(authTokenResolver.resolve("Bearer token")).thenReturn("token");
    when(authService.requireUser("token"))
        .thenReturn(new User("Authenticated", "authenticated@example.com", "hash", role));
  }
}
