package br.com.promova.analysis.engine.openrouter;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.promova.analysis.dto.EvidenceAnalysisRequest;
import br.com.promova.analysis.dto.EvidenceAnalysisResponse;
import br.com.promova.analysis.engine.Confidence;
import br.com.promova.analysis.engine.ai.AiChatClient;
import br.com.promova.framework.CareerFramework;
import br.com.promova.framework.CareerLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenRouterAnalysisEngineTest {
  private final CareerFramework careerFramework =
      new CareerFramework(
          Map.of(
              "L3",
              new CareerLevel("Works with guidance, limited ownership"),
              "L4",
              new CareerLevel("Works independently, improves systems, measurable impact")));

  @Test
  void mapsAiJsonIntoAnalysisResponse() {
    AiChatClient client =
        (messages) ->
            """
            {
              "estimatedLevel": "L4",
              "confidence": "high",
              "reasoning": "Evidence shows measurable system improvement.",
              "competencies": ["Ownership", "Code Quality"],
              "suggestions": ["Add business impact."]
            }
            """;
    OpenRouterAnalysisEngine engine = new OpenRouterAnalysisEngine(client, new ObjectMapper());

    EvidenceAnalysisResponse response =
        engine.analyze(
            new EvidenceAnalysisRequest(
                "Refactored the checkout service and improved latency by 30%", "L3", "L4"),
            careerFramework);

    assertThat(response.estimatedLevel()).isEqualTo("L4");
    assertThat(response.confidence()).isEqualTo(Confidence.HIGH);
    assertThat(response.competencies()).containsExactly("Ownership", "Code Quality");
    assertThat(response.suggestions()).containsExactly("Add business impact.");
  }

  @Test
  void fallsBackToCurrentLevelWhenAiReturnsUnknownLevel() {
    AiChatClient client =
        (messages) ->
            """
            ```json
            {
              "estimatedLevel": "L9",
              "confidence": "low",
              "reasoning": "Evidence is vague.",
              "competencies": [],
              "suggestions": ["Add concrete scope and metrics."]
            }
            ```
            """;
    OpenRouterAnalysisEngine engine = new OpenRouterAnalysisEngine(client, new ObjectMapper());

    EvidenceAnalysisResponse response =
        engine.analyze(
            new EvidenceAnalysisRequest("Helped with backend tasks", "L3", "L4"), careerFramework);

    assertThat(response.estimatedLevel()).isEqualTo("L3");
    assertThat(response.confidence()).isEqualTo(Confidence.LOW);
  }
}
