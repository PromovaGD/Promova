package br.com.promova.github.connection;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.promova.user.User;
import br.com.promova.user.UserRepository;
import br.com.promova.user.UserRole;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class GithubConnectionSettingsRepositoryTest {
  @Autowired private GithubConnectionSettingsRepository settingsRepository;
  @Autowired private UserRepository userRepository;

  @Test
  void persistsOneSettingsRowPerUserAndScopesReadsByOwner() {
    User first = userRepository.save(new User("First", "first@example.com", "hash", UserRole.EMPLOYEE));
    User second = userRepository.save(new User("Second", "second@example.com", "hash", UserRole.EMPLOYEE));

    GithubConnectionSettings firstSettings = new GithubConnectionSettings(first);
    firstSettings.configure("acme/project", "octocat");
    firstSettings.recordSync(Instant.parse("2026-08-09T12:00:00Z"), "SUCCESS");
    settingsRepository.save(firstSettings);

    assertThat(settingsRepository.findByUserId(first.getId())).get().satisfies(settings -> {
      assertThat(settings.getRepoSlug()).isEqualTo("acme/project");
      assertThat(settings.getAuthorLogin()).isEqualTo("octocat");
      assertThat(settings.getLastSyncOutcome()).isEqualTo("SUCCESS");
    });
    assertThat(settingsRepository.findByUserId(second.getId())).isEmpty();
  }
}
