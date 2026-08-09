package br.com.promova.github.connection;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GithubConnectionSettingsRepository
    extends JpaRepository<GithubConnectionSettings, Long> {
  Optional<GithubConnectionSettings> findByUserId(Long userId);
}
