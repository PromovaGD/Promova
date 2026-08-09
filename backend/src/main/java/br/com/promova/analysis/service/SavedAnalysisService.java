package br.com.promova.analysis.service;

import br.com.promova.analysis.dto.EvidenceAnalysisResponse;
import br.com.promova.analysis.dto.SavedAnalysisRequest;
import br.com.promova.analysis.dto.SavedAnalysisResponse;
import br.com.promova.analysis.persistence.SavedAnalysis;
import br.com.promova.analysis.persistence.SavedAnalysisRepository;
import br.com.promova.evidence.Evidence;
import br.com.promova.framework.CareerFramework;
import br.com.promova.user.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SavedAnalysisService {
  private final SavedAnalysisRepository savedAnalysisRepository;
  private final ObjectMapper objectMapper;

  public SavedAnalysisService(
      SavedAnalysisRepository savedAnalysisRepository, ObjectMapper objectMapper) {
    this.savedAnalysisRepository = savedAnalysisRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public List<SavedAnalysisResponse> listForUser(User user, Instant from, Instant to) {
    return savedAnalysisRepository.findByUserAndDateRange(user, from, to).stream()
        .map(this::toResponseForTransaction)
        .toList();
  }

  @Transactional
  public SavedAnalysisResponse save(User user, SavedAnalysisRequest request) {
    SavedAnalysis saved =
        savedAnalysisRepository.save(
            new SavedAnalysis(
                request.externalId(),
                user,
                request.source(),
                request.sourceMeta(),
                request.evidence(),
                request.currentLevel(),
                request.targetLevel(),
                request.impactLevel(),
                request.confidence(),
                request.justification(),
                writeJson(safeList(request.competencies())),
                writeJson(safeList(request.suggestions())),
                request.readiness(),
                request.createdAt()));

    return toResponseForTransaction(saved);
  }

  /**
   * Persists an engine result together with the immutable source snapshot used for that result.
   * The caller owns the surrounding transaction and is responsible for transitioning the evidence
   * only after this object has been built successfully.
   */
  @Transactional
  public SavedAnalysisResponse saveEngineResult(
      Evidence evidence,
      String currentLevel,
      String targetLevel,
      EvidenceAnalysisResponse engineResult,
      CareerFramework careerFramework,
      Instant createdAt) {
    SavedAnalysis saved =
        savedAnalysisRepository.save(
            new SavedAnalysis(
                evidence,
                currentLevel,
                targetLevel,
                engineResult.estimatedLevel(),
                engineResult.confidence().value(),
                engineResult.reasoning(),
                writeJson(safeList(engineResult.competencies())),
                writeJson(safeList(engineResult.suggestions())),
                readinessFor(engineResult.estimatedLevel(), targetLevel, careerFramework),
                createdAt));

    return toResponseForTransaction(saved);
  }

  @Transactional
  public void clearForUser(User user, Instant from, Instant to) {
    if (from == null && to == null) {
      savedAnalysisRepository.deleteAllByUser(user);
      return;
    }

    savedAnalysisRepository.deleteByUserAndDateRange(user, from, to);
  }

  SavedAnalysisResponse toResponseForTransaction(SavedAnalysis saved) {
    return new SavedAnalysisResponse(
        saved.getExternalId(),
        saved.getUser().getId(),
        saved.getSource(),
        saved.getSourceMeta(),
        saved.getEvidence(),
        saved.getCurrentLevel(),
        saved.getTargetLevel(),
        saved.getImpactLevel(),
        saved.getConfidence(),
        saved.getJustification(),
        readList(saved.getCompetenciesJson()),
        readList(saved.getSuggestionsJson()),
        saved.getReadiness(),
        saved.getCreatedAt());
  }

  private List<String> safeList(List<String> values) {
    return values == null ? Collections.emptyList() : values;
  }

  private String writeJson(List<String> values) {
    try {
      return objectMapper.writeValueAsString(values);
    } catch (JsonProcessingException error) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao serializar lista.");
    }
  }

  private List<String> readList(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (JsonProcessingException error) {
      return Collections.emptyList();
    }
  }

  private String readinessFor(
      String impactLevel, String targetLevel, CareerFramework careerFramework) {
    boolean reachesTarget =
        impactLevel.equals(targetLevel) || careerFramework.isAbove(targetLevel, impactLevel);
    if (reachesTarget) {
      return "Esta evidência está alinhada com o alvo atual de " + targetLevel + ".";
    }

    return "Esta evidência ainda está abaixo do alvo de "
        + targetLevel
        + ", então pode ser fortalecida com resultados mensuráveis.";
  }
}
