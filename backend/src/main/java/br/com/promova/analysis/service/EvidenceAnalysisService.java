package br.com.promova.analysis.service;

import br.com.promova.analysis.dto.EvidenceAnalysisRequest;
import br.com.promova.analysis.dto.EvidenceAnalysisResponse;
import br.com.promova.analysis.dto.SavedAnalysisResponse;
import br.com.promova.analysis.engine.AnalysisEngine;
import br.com.promova.analysis.persistence.SavedAnalysis;
import br.com.promova.analysis.persistence.SavedAnalysisRepository;
import br.com.promova.evidence.Evidence;
import br.com.promova.evidence.EvidenceRepository;
import br.com.promova.evidence.EvidenceStatus;
import br.com.promova.framework.CareerFramework;
import br.com.promova.framework.FrameworkProvider;
import br.com.promova.profile.CareerProfile;
import br.com.promova.profile.CareerProfileRepository;
import br.com.promova.user.User;
import java.time.Instant;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EvidenceAnalysisService {
  private static final Logger LOGGER = LoggerFactory.getLogger(EvidenceAnalysisService.class);
  private static final int MAX_LIST_ITEMS = 10;
  private static final int MAX_LIST_ITEM_LENGTH = 200;
  private final FrameworkProvider frameworkProvider;
  private final AnalysisEngine analysisEngine;
  private final EvidenceRepository evidenceRepository;
  private final CareerProfileRepository profileRepository;
  private final SavedAnalysisRepository savedAnalysisRepository;
  private final SavedAnalysisService savedAnalysisService;

  public EvidenceAnalysisService(
      FrameworkProvider frameworkProvider,
      AnalysisEngine analysisEngine,
      EvidenceRepository evidenceRepository,
      CareerProfileRepository profileRepository,
      SavedAnalysisRepository savedAnalysisRepository,
      SavedAnalysisService savedAnalysisService) {
    this.frameworkProvider = frameworkProvider;
    this.analysisEngine = analysisEngine;
    this.evidenceRepository = evidenceRepository;
    this.profileRepository = profileRepository;
    this.savedAnalysisRepository = savedAnalysisRepository;
    this.savedAnalysisService = savedAnalysisService;
  }

  /**
   * Internal engine adapter retained for focused engine tests. It is deliberately not exposed as
   * an HTTP endpoint; browser callers must use {@link #analyzeOwnedEvidence(User, Long)}.
   */
  public EvidenceAnalysisResponse analyze(EvidenceAnalysisRequest request) {
    CareerFramework careerFramework = frameworkProvider.load();
    return analysisEngine.analyze(request, careerFramework);
  }

  /**
   * Loads all analysis inputs from server-owned state, invokes the engine, and commits the saved
   * result plus the PENDING -> ANALYZED transition in one transaction.
   */
  @Transactional
  public SavedAnalysisResponse analyzeOwnedEvidence(User authenticatedUser, Long evidenceId) {
    return analyzeOwnedEvidence(authenticatedUser, evidenceId, null);
  }

  @Transactional
  public SavedAnalysisResponse analyzeOwnedEvidence(
      User authenticatedUser, Long evidenceId, String userObservation) {
    Instant startedAt = Instant.now();
    Evidence evidence =
        evidenceRepository
            .findByIdAndUserIdForUpdate(evidenceId, authenticatedUser.getId())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidência não encontrada."));

    Optional<SavedAnalysis> existing =
        savedAnalysisRepository.findByEvidenceIdAndUserId(evidenceId, authenticatedUser.getId());
    if (existing.isPresent()) {
      return savedAnalysisService.toResponseForTransaction(existing.get());
    }

    if (evidence.getStatus() != EvidenceStatus.PENDING) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Evidência não pode ser analisada neste estado.");
    }

    CareerFramework careerFramework = frameworkProvider.load();
    CareerProfile profile =
        profileRepository
            .findByUserId(authenticatedUser.getId())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Perfil de carreira não encontrado para esta conta."));
    careerFramework.validateProgression(profile.getCurrentLevel(), profile.getTargetLevel());

    EvidenceAnalysisRequest request =
        new EvidenceAnalysisRequest(
            evidence.getContent(),
            normalizeObservation(userObservation),
            profile.getCurrentLevel(),
            profile.getTargetLevel());
    EvidenceAnalysisResponse engineResult;
    try {
      engineResult = analysisEngine.analyze(request, careerFramework);
      validateEngineResult(engineResult, careerFramework, profile);
    } catch (RuntimeException error) {
      LOGGER.warn(
          "analysis_failed evidenceId={} durationMs={} errorType={}",
          evidenceId,
          Duration.between(startedAt, Instant.now()).toMillis(),
          error.getClass().getSimpleName());
      throw error;
    }

    var savedResponse =
        savedAnalysisService.saveEngineResult(
            evidence,
            profile.getCurrentLevel(),
            profile.getTargetLevel(),
            request.userObservation(),
            engineResult,
            careerFramework,
            Instant.now());
    evidence.markAnalyzed();
    evidenceRepository.save(evidence);
    LOGGER.info(
        "analysis_succeeded evidenceId={} analysisId={} durationMs={}",
        evidenceId,
        savedResponse == null ? null : savedResponse.analysisId(),
        Duration.between(startedAt, Instant.now()).toMillis());
    return savedResponse;
  }

  private void validateEngineResult(
      EvidenceAnalysisResponse result, CareerFramework careerFramework, CareerProfile profile) {
    if (result == null
        || result.estimatedLevel() == null
        || result.confidence() == null
        || result.reasoning() == null
        || result.reasoning().isBlank()
        || result.reasoning().length() > 4000
        || invalidList(result.competencies())
        || invalidList(result.suggestions())
        || !careerFramework.containsLevel(result.estimatedLevel())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Analysis engine returned an invalid result.");
    }

    if (!careerFramework.containsLevel(profile.getCurrentLevel())
        || !careerFramework.containsLevel(profile.getTargetLevel())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Perfil de carreira contém níveis fora do framework atual.");
    }
  }

  private String normalizeObservation(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().replaceAll("\\s+", " ");
    if (normalized.length() > 2000) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "A observação deve ter no máximo 2.000 caracteres.");
    }
    return normalized;
  }

  private boolean invalidList(java.util.List<String> values) {
    return values == null
        || values.size() > MAX_LIST_ITEMS
        || values.stream()
            .anyMatch(
                value ->
                    value == null
                        || value.isBlank()
                        || value.length() > MAX_LIST_ITEM_LENGTH);
  }
}
