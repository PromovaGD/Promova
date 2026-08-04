package br.com.promova.analysis.engine.openrouter;

import br.com.promova.analysis.dto.EvidenceAnalysisRequest;
import br.com.promova.analysis.dto.EvidenceAnalysisResponse;
import br.com.promova.analysis.engine.AnalysisEngine;
import br.com.promova.analysis.engine.Confidence;
import br.com.promova.analysis.engine.ai.AiChatClient;
import br.com.promova.analysis.engine.ai.AiChatMessage;
import br.com.promova.framework.CareerFramework;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@ConditionalOnProperty(name = "promova.analysis.engine", havingValue = "openrouter")
public class OpenRouterAnalysisEngine implements AnalysisEngine {
  private static final String SYSTEM_PROMPT =
      """
      You are Promova's career evidence reviewer.

      Your job is to review one career evidence entry against the provided career framework.
      Use only the provided evidence and framework. Do not invent facts, metrics, scope, seniority, or business impact.

      Review rules:
      - estimatedLevel must be one of the provided framework level keys.
      - Prefer the target level only when the evidence demonstrates the behaviors described for that level.
      - Keep the current level when evidence is relevant but does not prove target-level scope or impact.
      - Use confidence "high" only when impact, ownership, and scope are clear.
      - Use confidence "medium" when the evidence is directionally strong but missing details.
      - Use confidence "low" when the evidence is vague, unsupported, or mostly task execution.
      - competencies must be concise career-signal labels, not long explanations.
      - suggestions must be actionable improvements the person can make to strengthen the evidence.

      Return only valid JSON. Do not include Markdown, code fences, or extra commentary.
      The JSON shape must be:
      {
        "estimatedLevel": "L3",
        "confidence": "low|medium|high",
        "reasoning": "short explanation",
        "competencies": ["label"],
        "suggestions": ["actionable suggestion"]
      }
      """;

  private final AiChatClient aiChatClient;
  private final ObjectMapper objectMapper;

  public OpenRouterAnalysisEngine(AiChatClient aiChatClient, ObjectMapper objectMapper) {
    this.aiChatClient = aiChatClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public EvidenceAnalysisResponse analyze(
      EvidenceAnalysisRequest request, CareerFramework careerFramework) {
    String modelResponse = aiChatClient.complete(messagesFor(request, careerFramework));
    return toResponse(modelResponse, request, careerFramework);
  }

  private List<AiChatMessage> messagesFor(
      EvidenceAnalysisRequest request, CareerFramework careerFramework) {
    return List.of(
        new AiChatMessage("system", SYSTEM_PROMPT),
        new AiChatMessage("user", userPrompt(request, careerFramework)));
  }

  private String userPrompt(EvidenceAnalysisRequest request, CareerFramework careerFramework) {
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("evidence", request.evidence());
    payload.put("currentLevel", request.currentLevel());
    payload.put("targetLevel", request.targetLevel());
    payload.set("careerFramework", objectMapper.valueToTree(careerFramework.levels()));

    try {
      return "Review this evidence using the provided framework:\n"
          + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
    } catch (IOException exception) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not build AI prompt", exception);
    }
  }

  private EvidenceAnalysisResponse toResponse(
      String modelResponse, EvidenceAnalysisRequest request, CareerFramework careerFramework) {
    try {
      JsonNode json = objectMapper.readTree(extractJson(modelResponse));
      return new EvidenceAnalysisResponse(
          normalizeLevel(json.path("estimatedLevel").asText(), request, careerFramework),
          confidence(json.path("confidence").asText()),
          requiredText(json, "reasoning"),
          stringList(json.path("competencies")),
          stringList(json.path("suggestions")));
    } catch (IOException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI response was not valid JSON", exception);
    }
  }

  private String normalizeLevel(
      String estimatedLevel, EvidenceAnalysisRequest request, CareerFramework careerFramework) {
    if (estimatedLevel != null && careerFramework.levels().containsKey(estimatedLevel)) {
      return estimatedLevel;
    }

    return careerFramework.resolveLevelOrDefault(request.currentLevel(), request.currentLevel());
  }

  private Confidence confidence(String value) {
    String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "low" -> Confidence.LOW;
      case "high" -> Confidence.HIGH;
      default -> Confidence.MEDIUM;
    };
  }

  private String requiredText(JsonNode json, String fieldName) {
    String value = json.path(fieldName).asText();
    if (value == null || value.isBlank()) {
      return "The AI reviewer completed the analysis but did not provide detailed reasoning.";
    }
    return value;
  }

  private List<String> stringList(JsonNode node) {
    if (!node.isArray()) {
      return List.of();
    }

    List<String> values = new ArrayList<>();
    Iterator<JsonNode> iterator = node.elements();
    while (iterator.hasNext()) {
      String value = iterator.next().asText();
      if (value != null && !value.isBlank()) {
        values.add(value);
      }
    }
    return values;
  }

  private String extractJson(String modelResponse) {
    String trimmed = modelResponse.trim();
    if (!trimmed.startsWith("```")) {
      return trimmed;
    }

    return trimmed.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
  }
}
