package br.com.promova.auth;

import br.com.promova.auth.dto.AuthResponse;
import br.com.promova.auth.dto.LoginRequest;
import br.com.promova.auth.dto.RegisterRequest;
import br.com.promova.auth.dto.UserSummaryResponse;
import br.com.promova.user.User;
import br.com.promova.user.UserRepository;
import br.com.promova.user.UserRole;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
  private static final int SESSION_DAYS = 14;

  private final UserRepository userRepository;
  private final AuthSessionRepository authSessionRepository;
  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  public AuthService(UserRepository userRepository, AuthSessionRepository authSessionRepository) {
    this.userRepository = userRepository;
    this.authSessionRepository = authSessionRepository;
  }

  @Transactional
  public AuthResponse register(RegisterRequest request) {
    if (userRepository.findByEmailIgnoreCase(request.email()).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado.");
    }

    User user =
        userRepository.save(
            new User(
                request.name().trim(),
                request.email(),
                passwordEncoder.encode(request.password()),
                UserRole.EMPLOYEE));

    return createSession(user);
  }

  @Transactional
  public AuthResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByEmailIgnoreCase(request.email())
            .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas."));

    return createSession(user);
  }

  @Transactional
  public void logout(String token) {
    authSessionRepository
        .findByTokenAndExpiresAtAfter(token, Instant.now())
        .ifPresent(authSessionRepository::delete);
  }

  @Transactional(readOnly = true)
  public User requireUser(String token) {
    return authSessionRepository
        .findByTokenAndExpiresAtAfter(token, Instant.now())
        .map(AuthSession::getUser)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida."));
  }

  @Transactional(readOnly = true)
  public UserSummaryResponse currentUser(String token) {
    return UserSummaryResponse.from(requireUser(token));
  }

  @Transactional
  public void purgeExpiredSessions() {
    authSessionRepository.deleteByExpiresAtBefore(Instant.now());
  }

  private AuthResponse createSession(User user) {
    purgeExpiredSessions();
    String token = UUID.randomUUID().toString();
    AuthSession session =
        authSessionRepository.save(
            new AuthSession(token, user, Instant.now().plus(SESSION_DAYS, ChronoUnit.DAYS)));
    return AuthResponse.of(session.getToken(), user);
  }
}
