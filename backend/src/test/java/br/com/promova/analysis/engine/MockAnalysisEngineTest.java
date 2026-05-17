package br.com.promova.analysis.engine;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.promova.analysis.dto.EvidenceAnalysisRequest;
import br.com.promova.framework.CareerFramework;
import br.com.promova.framework.CareerLevel;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MockAnalysisEngineTest {
  private final MockAnalysisEngine engine = new MockAnalysisEngine();
  private final CareerFramework careerFramework =
      new CareerFramework(
          Map.of(
              "L3",
              new CareerLevel("Works with guidance, limited ownership"),
              "L4",
              new CareerLevel("Works independently, improves systems, measurable impact")));

  @Test
  void estimatesTargetLevelForHighImpactEvidence() {
    var request =
        new EvidenceAnalysisRequest(
            "Refactored payment module and increased test coverage to 85%", "L3", "L4");

    var response = engine.analyze(request, careerFramework);

    assertThat(response.estimatedLevel()).isEqualTo("L4");
    assertThat(response.competencies()).containsExactly("Code Quality", "Ownership");
  }

  @Test
  void keepsCurrentLevelForSupportEvidence() {
    var request = new EvidenceAnalysisRequest("Helped team fix bugs", "L3", "L4");

    var response = engine.analyze(request, careerFramework);

    assertThat(response.estimatedLevel()).isEqualTo("L3");
    assertThat(response.competencies()).isEmpty();
  }

  @Test
  void keepsCurrentLevelForNeutralEvidence() {
    var request = new EvidenceAnalysisRequest("Worked on backend services", "L3", "L4");

    var response = engine.analyze(request, careerFramework);

    assertThat(response.estimatedLevel()).isEqualTo("L3");
  }
}
