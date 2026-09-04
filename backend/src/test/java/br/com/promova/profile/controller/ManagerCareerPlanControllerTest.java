package br.com.promova.profile.controller;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.config.ApiExceptionHandler;
import br.com.promova.config.WebConfig;
import br.com.promova.profile.CareerPlanService;
import br.com.promova.profile.dto.CareerPlanUpdateRequest;
import br.com.promova.profile.dto.ProfileResponse;
import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;

@WebMvcTest(ManagerCareerPlanController.class)
@Import({WebConfig.class, ApiExceptionHandler.class})
class ManagerCareerPlanControllerTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthService authService;
  @MockitoBean private AuthTokenResolver authTokenResolver;
  @MockitoBean private CareerPlanService careerPlanService;

  @Test
  void employeesCannotReadOrMutateAnotherUsersPlan() throws Exception {
    authenticate(UserRole.EMPLOYEE);

    mockMvc
        .perform(
            get("/manager/employees/7/career-plan")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            put("/manager/employees/7/career-plan")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"jobRoleId":3,"currentLevel":"L3","targetLevel":"L4","characteristics":[]}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void managersCanUpdateAnEmployeesPlan() throws Exception {
    authenticate(UserRole.MANAGER);
    CareerPlanUpdateRequest request =
        new CareerPlanUpdateRequest(3L, "L3", "L4", List.of("Mentoria"));
    when(careerPlanService.updatePlan(7L, request))
        .thenReturn(new ProfileResponse("L3", "L4", List.of()));

    mockMvc
        .perform(
            put("/manager/employees/7/career-plan")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"jobRoleId":3,"currentLevel":"L3","targetLevel":"L4","characteristics":["Mentoria"]}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentLevel").value("L3"));

    verify(careerPlanService).updatePlan(7L, request);
  }

  private void authenticate(UserRole role) {
    when(authTokenResolver.resolve("Bearer token")).thenReturn("token");
    when(authService.requireUser("token"))
        .thenReturn(new User("Authenticated", "authenticated@example.com", "hash", role));
  }
}
