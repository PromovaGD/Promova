package br.com.promova.analysis.engine;

import br.com.promova.analysis.dto.EvidenceAnalysisRequest;
import br.com.promova.analysis.dto.EvidenceAnalysisResponse;
import br.com.promova.framework.CareerFramework;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "test"})
@ConditionalOnProperty(name = "promova.analysis.engine", havingValue = "mock", matchIfMissing = true)
public class MockAnalysisEngine implements AnalysisEngine {
  private static final List<String> IMPACT_KEYWORDS =
      List.of("refactor", "improve", "increase", "optimize");
  private static final List<String> SUPPORT_KEYWORDS = List.of("help", "support", "assist");
  private static final List<String> SUGGESTIONS =
      List.of(
          "Include measurable impact (numbers, metrics)",
          "Clarify your specific contribution",
          "Highlight cross-team collaboration if applicable");

  @Override
  public EvidenceAnalysisResponse analyze(
      EvidenceAnalysisRequest request, CareerFramework careerFramework) {
    String normalizedEvidence = request.evidence().toLowerCase(Locale.ROOT);
    String estimatedLevel = estimateLevel(request, careerFramework, normalizedEvidence);
    List<String> competencies = detectCompetencies(normalizedEvidence);

    return new EvidenceAnalysisResponse(
        estimatedLevel,
        Confidence.MEDIUM,
        buildReasoning(estimatedLevel, request),
        competencies,
        SUGGESTIONS);
  }

  private String estimateLevel(
      EvidenceAnalysisRequest request, CareerFramework careerFramework, String normalizedEvidence) {
    if (containsAny(normalizedEvidence, IMPACT_KEYWORDS)) {
      return careerFramework.resolveLevelOrDefault(request.targetLevel(), request.currentLevel());
    }

    return careerFramework.resolveLevelOrDefault(request.currentLevel(), request.currentLevel());
  }

  private List<String> detectCompetencies(String normalizedEvidence) {
    List<String> competencies = new ArrayList<>();

    if (containsAny(normalizedEvidence, List.of("test", "coverage"))) {
      competencies.add("Code Quality");
    }

    if (containsAny(normalizedEvidence, List.of("refactor", "improve"))) {
      competencies.add("Ownership");
    }

    if (containsAny(normalizedEvidence, List.of("led", "mentored"))) {
      competencies.add("Leadership");
    }

    return competencies;
  }

  private String buildReasoning(String estimatedLevel, EvidenceAnalysisRequest request) {
    if (estimatedLevel.equals(request.targetLevel())) {
      return "Demonstrates ownership and measurable improvement in system quality";
    }

    if (containsAny(request.evidence().toLowerCase(Locale.ROOT), SUPPORT_KEYWORDS)) {
      return "Shows contribution to team delivery, but needs stronger evidence of independent impact";
    }

    return "Evidence is relevant, but does not yet show clear measurable impact for the target level";
  }

  private boolean containsAny(String evidence, List<String> keywords) {
    return keywords.stream().anyMatch(evidence::contains);
  }
}
