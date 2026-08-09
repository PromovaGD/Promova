package br.com.promova.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.promova.auth.AuthService;
import br.com.promova.auth.AuthTokenResolver;
import br.com.promova.auth.dto.AuthResponse;
import br.com.promova.auth.dto.UserSummaryResponse;
import br.com.promova.config.ApiExceptionHandler;
import br.com.promova.config.WebConfig;
import br.com.promova.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({WebConfig.class, ApiExceptionHandler.class})
class AuthControllerSecurityTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthService authService;
  @MockitoBean private AuthTokenResolver authTokenResolver;

  @Test
  void keepsRegistrationPublic() throws Exception {
    when(authService.register(any()))
        .thenReturn(
            new AuthResponse(
                "session-token",
                new UserSummaryResponse(1L, "Employee", "employee@example.com", UserRole.EMPLOYEE)));

    mockMvc
        .perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Employee",
                      "email": "employee@example.com",
                      "password": "secret123"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value("session-token"))
        .andExpect(jsonPath("$.user.role").value("EMPLOYEE"));
  }

  @Test
  void keepsLoginPublic() throws Exception {
    when(authService.login(any()))
        .thenReturn(
            new AuthResponse(
                "session-token",
                new UserSummaryResponse(1L, "Employee", "employee@example.com", UserRole.EMPLOYEE)));

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "employee@example.com",
                      "password": "secret123"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value("session-token"));
  }

  @Test
  void protectsCurrentUserLookupWhenTokenIsMissing() throws Exception {
    mockMvc
        .perform(get("/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Token ausente."));
  }
}
