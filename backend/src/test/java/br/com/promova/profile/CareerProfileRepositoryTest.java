package br.com.promova.profile;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.promova.user.User;
import br.com.promova.user.UserRepository;
import br.com.promova.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CareerProfileRepositoryTest {
  @Autowired private CareerProfileRepository profileRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TestEntityManager entityManager;

  @Test
  void persistsProfileAcrossEntityManagerReloadForTheOwningUser() {
    User user =
        userRepository.save(new User("Employee", "employee@example.com", "hash", UserRole.EMPLOYEE));
    profileRepository.save(new CareerProfile(user, "L3", "L4"));
    entityManager.flush();
    entityManager.clear();

    CareerProfile reloaded = profileRepository.findByUserId(user.getId()).orElseThrow();

    assertThat(reloaded.getCurrentLevel()).isEqualTo("L3");
    assertThat(reloaded.getTargetLevel()).isEqualTo("L4");
    assertThat(reloaded.getUser().getId()).isEqualTo(user.getId());
  }
}
