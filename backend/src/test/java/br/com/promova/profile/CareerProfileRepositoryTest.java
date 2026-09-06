package br.com.promova.profile;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.promova.user.User;
import br.com.promova.user.UserRepository;
import br.com.promova.user.UserRole;
import br.com.promova.organization.JobRoleRepository;
import br.com.promova.organization.JobRoleStatus;
import java.util.List;
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
  @Autowired private JobRoleRepository jobRoleRepository;
  @Autowired private CareerObjectiveRepository objectiveRepository;
  @Autowired private TestEntityManager entityManager;

  @Test
  void persistsProfileAcrossEntityManagerReloadForTheOwningUser() {
    User user =
        userRepository.save(new User("Employee", "employee@example.com", "hash", UserRole.EMPLOYEE));
    var role =
        jobRoleRepository.findFirstByStatusOrderByNameAsc(JobRoleStatus.ACTIVE).orElseThrow();
    CareerProfile profile =
        profileRepository.save(
            new CareerProfile(user, role, "L3", "L4", List.of("Mentoria", "Ownership")));
    User manager =
        userRepository.save(new User("Manager", "manager-plan@example.com", "hash", UserRole.MANAGER));
    objectiveRepository.save(
        new CareerObjective(
            profile, "Lead a release", java.time.LocalDate.parse("2026-12-01"), manager));
    entityManager.flush();
    entityManager.clear();

    CareerProfile reloaded = profileRepository.findByUserId(user.getId()).orElseThrow();

    assertThat(reloaded.getCurrentLevel()).isEqualTo("L3");
    assertThat(reloaded.getTargetLevel()).isEqualTo("L4");
    assertThat(reloaded.getJobRole().getId()).isEqualTo(role.getId());
    assertThat(reloaded.getCharacteristics()).containsExactly("Mentoria", "Ownership");
    assertThat(reloaded.getUser().getId()).isEqualTo(user.getId());
    CareerObjective objective =
        objectiveRepository.findByCareerProfileIdOrderByCreatedAtAsc(reloaded.getId()).get(0);
    assertThat(objective.getText()).isEqualTo("Lead a release");
    assertThat(objective.getUpdatedBy().getId()).isEqualTo(manager.getId());
    assertThat(objective.getCreatedAt()).isNotNull();
  }
}
