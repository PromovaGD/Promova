package br.com.promova.framework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CareerFrameworkTest {
  private final CareerFramework framework =
      new CareerFramework(
          new java.util.LinkedHashMap<>() {
            {
              put("L2", new CareerLevel("Engineer I"));
              put("L10", new CareerLevel("Engineer II"));
              put("L11", new CareerLevel("Senior Engineer"));
            }
          });

  @Test
  void comparesLevelsUsingDeclaredOrderInsteadOfLexicalOrder() {
    assertThat(framework.levelKeys()).containsExactly("L2", "L10", "L11");
    assertThat(framework.isAbove("L2", "L10")).isTrue();
    assertThat(framework.isAbove("L10", "L2")).isFalse();
  }

  @Test
  void rejectsUnknownEqualAndReversedProgressions() {
    assertThatThrownBy(() -> framework.validateProgression("L2", "L99"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("targetLevel");
    assertThatThrownBy(() -> framework.validateProgression("L10", "L10"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("above");
    assertThatThrownBy(() -> framework.validateProgression("L11", "L2"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("above");
  }

  @Test
  void resolvesFrameworkCompatibleDefaults() {
    assertThat(framework.defaultCurrentLevel()).isEqualTo("L2");
    assertThat(framework.defaultTargetLevel()).isEqualTo("L10");
    assertThat(framework.resolveLevelOrDefault("unknown", "also-unknown")).isEqualTo("L2");
  }

  @Test
  void keepsTheExistingDemoDefaultsForTheCurrentFramework() {
    java.util.LinkedHashMap<String, CareerLevel> levels = new java.util.LinkedHashMap<>();
    levels.put("L3", new CareerLevel("Software Engineer I"));
    levels.put("L4", new CareerLevel("Software Engineer II"));
    levels.put("L5", new CareerLevel("Senior Software Engineer"));

    CareerFramework currentFramework = new CareerFramework(levels);

    assertThat(currentFramework.defaultCurrentLevel()).isEqualTo("L3");
    assertThat(currentFramework.defaultTargetLevel()).isEqualTo("L4");
  }
}
