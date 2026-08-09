package br.com.promova.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {
  @Mock private AuthService authService;
  @Mock private AuthTokenResolver authTokenResolver;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;

  private AuthInterceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor = new AuthInterceptor(authService, authTokenResolver);
    when(request.getMethod()).thenReturn("GET");
    when(request.getContextPath()).thenReturn("");
    when(request.getRequestURI()).thenReturn("/analyze");
  }

  @Test
  void rejectsMissingTokenWithUnauthorized() {
    when(authTokenResolver.resolve((String) null)).thenReturn(null);

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> interceptor.preHandle(request, response, null));

    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void rejectsEmployeeFromAdminRoutesWithForbidden() {
    User employee = new User("Employee", "employee@example.com", "hash", UserRole.EMPLOYEE);
    when(request.getRequestURI()).thenReturn("/admin/employees");
    when(authTokenResolver.resolve("Bearer employee-token")).thenReturn("employee-token");
    when(request.getHeader("Authorization")).thenReturn("Bearer employee-token");
    when(authService.requireUser("employee-token")).thenReturn(employee);

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> interceptor.preHandle(request, response, null));

    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void allowsAuthenticatedAdminRequests() {
    User admin = new User("Admin", "admin@example.com", "hash", UserRole.ADMIN);
    when(request.getRequestURI()).thenReturn("/admin/employees");
    when(request.getHeader("Authorization")).thenReturn("Bearer admin-token");
    when(authTokenResolver.resolve("Bearer admin-token")).thenReturn("admin-token");
    when(authService.requireUser("admin-token")).thenReturn(admin);

    assertThat(interceptor.preHandle(request, response, null)).isTrue();
  }

  @Test
  void leavesRegisterAndLoginPublic() {
    when(request.getRequestURI()).thenReturn("/auth/register");

    assertThat(interceptor.preHandle(request, response, null)).isTrue();
    verifyNoInteractions(authService, authTokenResolver);
  }
}
