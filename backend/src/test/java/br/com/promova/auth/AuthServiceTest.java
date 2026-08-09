package br.com.promova.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.promova.auth.dto.AuthResponse;
import br.com.promova.auth.dto.RegisterRequest;
import br.com.promova.user.User;
import br.com.promova.user.UserRepository;
import br.com.promova.user.UserRole;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
  @Mock private UserRepository userRepository;
  @Mock private AuthSessionRepository authSessionRepository;

  @Test
  void rejectsInvalidOrExpiredSessionsWithUnauthorized() {
    AuthService authService = new AuthService(userRepository, authSessionRepository);
    when(authSessionRepository.findByTokenAndExpiresAtAfter(anyString(), any(Instant.class)))
        .thenReturn(Optional.empty());

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> authService.requireUser("expired-token"));

    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void registrationCreatesAnEmployeeSession() {
    AuthService authService = new AuthService(userRepository, authSessionRepository);
    when(userRepository.findByEmailIgnoreCase("employee@example.com")).thenReturn(Optional.empty());
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(authSessionRepository.save(any(AuthSession.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    AuthResponse response =
        authService.register(
            new RegisterRequest("Employee", "employee@example.com", "secret123"));

    assertThat(response.token()).isNotBlank();
    assertThat(response.user().role()).isEqualTo(UserRole.EMPLOYEE);
    verify(authSessionRepository).deleteByExpiresAtBefore(any(Instant.class));
    verify(authSessionRepository).save(any(AuthSession.class));
  }
}
