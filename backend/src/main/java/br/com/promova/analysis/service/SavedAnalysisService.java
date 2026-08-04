package br.com.promova.analysis.service;

import br.com.promova.analysis.dto.SavedAnalysisRequest;
import br.com.promova.analysis.dto.SavedAnalysisResponse;
import br.com.promova.analysis.persistence.SavedAnalysis;
import br.com.promova.analysis.persistence.SavedAnalysisRepository;
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
        .map(this::toResponse)
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

    return toResponse(saved);
  }

  @Transactional
  public void clearForUser(User user, Instant from, Instant to) {
    if (from == null && to == null) {
      savedAnalysisRepository.deleteAllByUser(user);
      return;
    }

    savedAnalysisRepository.deleteByUserAndDateRange(user, from, to);
  }

  private SavedAnalysisResponse toResponse(SavedAnalysis saved) {
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
}
