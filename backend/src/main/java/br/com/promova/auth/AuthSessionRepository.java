package br.com.promova.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {
  @EntityGraph(attributePaths = "user")
  Optional<AuthSession> findByTokenAndExpiresAtAfter(String token, Instant now);

  void deleteByExpiresAtBefore(Instant now);
}
