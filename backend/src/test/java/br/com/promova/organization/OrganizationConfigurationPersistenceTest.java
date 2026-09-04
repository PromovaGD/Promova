package br.com.promova.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class OrganizationConfigurationPersistenceTest {
  @Autowired private TerminologySettingsRepository terminologyRepository;
  @Autowired private JobRoleRepository jobRoleRepository;

  @Test
  void persistsTerminologyAndOrderedFrameworkLevels() {
    TerminologySettings settings =
        terminologyRepository.findById(TerminologySettings.SINGLETON_ID).orElseThrow();
    settings.update("Líder", "Talento", "Trilha", "Estágio", "Forças", "Meta");
    terminologyRepository.saveAndFlush(settings);

    JobRole role =
        jobRoleRepository.saveAndFlush(
            new JobRole("Platform", "Platform engineering", List.of("L3", "L4")));

    assertThat(
            terminologyRepository
                .findById(TerminologySettings.SINGLETON_ID)
                .orElseThrow()
                .getEmployeeLabel())
        .isEqualTo("Talento");
    assertThat(jobRoleRepository.findById(role.getId()).orElseThrow().getAllowedLevelIds())
        .containsExactly("L3", "L4");
  }
}
