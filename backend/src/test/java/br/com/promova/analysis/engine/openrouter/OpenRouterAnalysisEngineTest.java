package br.com.promova.analysis.engine.openrouter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.promova.analysis.dto.EvidenceAnalysisRequest;
import br.com.promova.analysis.dto.EvidenceAnalysisResponse;
import br.com.promova.analysis.engine.Confidence;
import br.com.promova.analysis.engine.ai.AiChatClient;
import br.com.promova.framework.CareerFramework;
import br.com.promova.framework.CareerLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
  void rejectsUnknownFrameworkLevelInsteadOfFabricatingAFallback() {
    AiChatClient client =
        (messages) ->
            """
            {
              "estimatedLevel": "L9",
              "confidence": "low",
              "reasoning": "Evidence is vague.",
              "competencies": [],
              "suggestions": ["Add concrete scope and metrics."]
            }
            """;
    OpenRouterAnalysisEngine engine = new OpenRouterAnalysisEngine(client, new ObjectMapper());

    assertThatThrownBy(
            () ->
                engine.analyze(
                    new EvidenceAnalysisRequest("Helped with backend tasks", "L3", "L4"),
                    careerFramework))
        .hasMessageContaining("invalid analysis");
  }

  @Test
  void sendsSourceAndEmployeeObservationAsSeparateNamedFields() {
    AtomicReference<String> prompt = new AtomicReference<>();
    AiChatClient client =
        messages -> {
          prompt.set(messages.get(1).content());
          return """
              {"estimatedLevel":"L3","confidence":"medium","reasoning":"Supported","competencies":[],"suggestions":[]}
              """;
        };
    OpenRouterAnalysisEngine engine = new OpenRouterAnalysisEngine(client, new ObjectMapper());

    engine.analyze(
        new EvidenceAnalysisRequest("Source body", "Employee context", "L3", "L4"),
        careerFramework);

    assertThat(prompt.get()).contains("\"sourceEvidence\" : \"Source body\"");
    assertThat(prompt.get()).contains("\"employeeObservation\" : \"Employee context\"");
  }

  @Test
  void rejectsUnknownConfidenceAndOversizedLists() {
    AiChatClient invalidConfidence =
        messages ->
            """
            {"estimatedLevel":"L3","confidence":"certain","reasoning":"Text","competencies":[],"suggestions":[]}
            """;
    OpenRouterAnalysisEngine confidenceEngine =
        new OpenRouterAnalysisEngine(invalidConfidence, new ObjectMapper());
    assertThatThrownBy(
            () ->
                confidenceEngine.analyze(
                    new EvidenceAnalysisRequest("Evidence", "L3", "L4"), careerFramework))
        .hasMessageContaining("confidence is invalid");

    AiChatClient oversized =
        messages ->
            """
            {"estimatedLevel":"L3","confidence":"low","reasoning":"Text","competencies":["1","2","3","4","5","6","7","8","9","10","11"],"suggestions":[]}
            """;
    OpenRouterAnalysisEngine listEngine = new OpenRouterAnalysisEngine(oversized, new ObjectMapper());
    assertThatThrownBy(
            () ->
                listEngine.analyze(
                    new EvidenceAnalysisRequest("Evidence", "L3", "L4"), careerFramework))
        .hasMessageContaining("exceeds its bounds");
  }
}
