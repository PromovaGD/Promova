package br.com.promova.manager;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.promova.analysis.service.SavedAnalysisService;
import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.config.ApiExceptionHandler;
import br.com.promova.config.WebConfig;
import br.com.promova.evidence.service.EvidenceService;
import br.com.promova.manager.controller.ManagerController;
import br.com.promova.profile.CareerObjectiveRepository;
import br.com.promova.profile.CareerProfileRepository;
import br.com.promova.user.User;
import br.com.promova.user.UserRepository;
import br.com.promova.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ManagerController.class)
@Import({WebConfig.class, ApiExceptionHandler.class})
class ManagerControllerSecurityTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthService authService;
  @MockitoBean private AuthTokenResolver authTokenResolver;
  @MockitoBean private UserRepository userRepository;
  @MockitoBean private SavedAnalysisService savedAnalysisService;
  @MockitoBean private CareerProfileRepository careerProfileRepository;
  @MockitoBean private CareerObjectiveRepository careerObjectiveRepository;
  @MockitoBean private EvidenceService evidenceService;

  @Test
  void rejectsAnonymousManagerRequests() throws Exception {
    mockMvc
        .perform(get("/manager/employees"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Autenticação necessária."));
  }

  @Test
  void rejectsEmployeeFromManagerRequests() throws Exception {
    User employee = user("Employee", "employee@example.com", UserRole.EMPLOYEE, 2L);
    when(authTokenResolver.resolve("Bearer employee-token")).thenReturn("employee-token");
    when(authService.requireUser("employee-token")).thenReturn(employee);

    mockMvc
        .perform(
            get("/manager/employees")
                .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
        .andExpect(status().isForbidden())
        .andExpect(
            jsonPath("$.message").value("Você não tem permissão para realizar esta ação."));
  }

  @Test
  void allowsManagerToListOtherUsers() throws Exception {
    User manager = user("Manager", "manager@example.com", UserRole.MANAGER, 1L);
    User employee = user("Employee", "employee@example.com", UserRole.EMPLOYEE, 2L);
    when(authTokenResolver.resolve("Bearer manager-token")).thenReturn("manager-token");
    when(authService.requireUser("manager-token")).thenReturn(manager);
    when(userRepository.findByRoleOrderByNameAsc(UserRole.EMPLOYEE))
        .thenReturn(java.util.List.of(employee));
    when(careerProfileRepository.findByUserId(2L)).thenReturn(java.util.Optional.empty());

    mockMvc
        .perform(
            get("/manager/employees")
                .header(HttpHeaders.AUTHORIZATION, "Bearer manager-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].email").value("employee@example.com"))
        .andExpect(jsonPath("$[0].role").value("EMPLOYEE"))
        .andExpect(jsonPath("$[0].activeObjectiveCount").value(0));
  }

  private User user(String name, String email, UserRole role, Long id) {
    User user = new User(name, email, "hash", role);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }
}
