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
      Source evidence and employee observation are separate inputs. Treat the observation only as bounded context about the source evidence.
      Use only those inputs and the framework. Never invent facts, metrics, scope, seniority, or business impact.

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
    payload.put("sourceEvidence", request.evidence());
    if (request.userObservation() == null) {
      payload.putNull("employeeObservation");
    } else {
      payload.put("employeeObservation", request.userObservation());
    }
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
          requiredLevel(json.path("estimatedLevel").asText(), careerFramework),
          confidence(json.path("confidence").asText()),
          requiredText(json, "reasoning"),
          stringList(json.path("competencies")),
          stringList(json.path("suggestions")));
    } catch (IOException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI response was not valid JSON", exception);
    }
  }

  private String requiredLevel(String estimatedLevel, CareerFramework careerFramework) {
    if (estimatedLevel != null && careerFramework.levels().containsKey(estimatedLevel)) {
      return estimatedLevel;
    }
    throw invalidResponse("estimatedLevel is not part of the career framework");
  }

  private Confidence confidence(String value) {
    return switch (value == null ? "" : value) {
      case "low" -> Confidence.LOW;
      case "medium" -> Confidence.MEDIUM;
      case "high" -> Confidence.HIGH;
      default -> throw invalidResponse("confidence is invalid");
    };
  }

  private String requiredText(JsonNode json, String fieldName) {
    String value = json.path(fieldName).asText();
    if (value == null || value.isBlank()) {
      throw invalidResponse(fieldName + " is required");
    }
    if (value.length() > 4000) throw invalidResponse(fieldName + " is too long");
    return value;
  }

  private List<String> stringList(JsonNode node) {
    if (!node.isArray()) {
      throw invalidResponse("list field is not an array");
    }

    List<String> values = new ArrayList<>();
    Iterator<JsonNode> iterator = node.elements();
    while (iterator.hasNext()) {
      JsonNode item = iterator.next();
      if (!item.isTextual()) {
        throw invalidResponse("list field contains a non-string item");
      }
      String value = item.asText();
      if (value != null && !value.isBlank()) {
        String normalized = value.trim();
        if (normalized.length() > 200 || values.size() >= 10) {
          throw invalidResponse("list field exceeds its bounds");
        }
        values.add(normalized);
      }
    }
    return values;
  }

  private String extractJson(String modelResponse) {
    if (modelResponse == null || modelResponse.isBlank()) {
      throw invalidResponse("response is empty");
    }
    return modelResponse.trim();
  }

  private ResponseStatusException invalidResponse(String detail) {
    return new ResponseStatusException(
        HttpStatus.BAD_GATEWAY, "AI provider returned an invalid analysis: " + detail);
  }
}
