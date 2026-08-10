package br.com.promova.admin;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.promova.analysis.service.SavedAnalysisService;
import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.config.ApiExceptionHandler;
import br.com.promova.config.WebConfig;
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

@WebMvcTest(br.com.promova.admin.controller.AdminController.class)
@Import({WebConfig.class, ApiExceptionHandler.class})
class AdminControllerSecurityTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthService authService;
  @MockitoBean private AuthTokenResolver authTokenResolver;
  @MockitoBean private UserRepository userRepository;
  @MockitoBean private SavedAnalysisService savedAnalysisService;

  @Test
  void rejectsAnonymousAdminRequests() throws Exception {
    mockMvc
        .perform(get("/admin/employees"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Token ausente."));
  }

  @Test
  void rejectsEmployeeFromAdminRequests() throws Exception {
    User employee = user("Employee", "employee@example.com", UserRole.EMPLOYEE, 2L);
    when(authTokenResolver.resolve("Bearer employee-token")).thenReturn("employee-token");
    when(authService.requireUser("employee-token")).thenReturn(employee);

    mockMvc
        .perform(
            get("/admin/employees")
                .header(HttpHeaders.AUTHORIZATION, "Bearer employee-token"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Acesso restrito a administradores."));
  }

  @Test
  void allowsAdminToListOtherUsers() throws Exception {
    User admin = user("Admin", "admin@example.com", UserRole.ADMIN, 1L);
    User employee = user("Employee", "employee@example.com", UserRole.EMPLOYEE, 2L);
    when(authTokenResolver.resolve("Bearer admin-token")).thenReturn("admin-token");
    when(authService.requireUser("admin-token")).thenReturn(admin);
    when(userRepository.findAllExcept(1L)).thenReturn(java.util.List.of(employee));

    mockMvc
        .perform(get("/admin/employees").header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].email").value("employee@example.com"))
        .andExpect(jsonPath("$[0].role").value("EMPLOYEE"));
  }

  private User user(String name, String email, UserRole role, Long id) {
    User user = new User(name, email, "hash", role);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }
}
