package br.com.promova.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.promova.profile.CareerProfile;
import br.com.promova.profile.CareerProfileRepository;
import br.com.promova.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:promova-seed-defaults;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
class DataSeederDefaultsIntegrationTest {
  @Autowired private UserRepository userRepository;
  @Autowired private CareerProfileRepository profileRepository;

  @Test
  void givesSeededEmployeesTheCurrentFrameworkDefaults() {
    Long joaoId = userRepository.findByEmailIgnoreCase("joao.silva@empresa.com").orElseThrow().getId();
    Long mariaId = userRepository.findByEmailIgnoreCase("maria.santos@empresa.com").orElseThrow().getId();

    CareerProfile joaoProfile = profileRepository.findByUserId(joaoId).orElseThrow();
    CareerProfile mariaProfile = profileRepository.findByUserId(mariaId).orElseThrow();

    assertThat(joaoProfile.getCurrentLevel()).isEqualTo("L3");
    assertThat(joaoProfile.getTargetLevel()).isEqualTo("L4");
    assertThat(mariaProfile.getCurrentLevel()).isEqualTo("L3");
    assertThat(mariaProfile.getTargetLevel()).isEqualTo("L4");
  }
}
