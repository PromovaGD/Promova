package br.com.promova.framework;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class JsonFrameworkProviderTest {
  @Test
  void preservesDeclaredJsonOrderAndTitlesForProfileChoices() {
    JsonFrameworkProvider provider =
        new JsonFrameworkProvider(
            new ObjectMapper().findAndRegisterModules(),
            new ClassPathResource("career-framework.json"));

    CareerFramework framework = provider.load();

    assertThat(framework.levelKeys()).containsExactly("L3", "L4", "L5");
    assertThat(framework.levels().get("L3").title()).isEqualTo("Software Engineer I");
    assertThat(framework.levels().get("L4").title()).isEqualTo("Software Engineer II");
  }
}
