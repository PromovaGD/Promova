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
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EvidenceAnalysisService {
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
            evidence.getEvidence(), profile.getCurrentLevel(), profile.getTargetLevel());
    EvidenceAnalysisResponse engineResult = analysisEngine.analyze(request, careerFramework);
    validateEngineResult(engineResult, careerFramework, profile);

    var savedResponse =
        savedAnalysisService.saveEngineResult(
            evidence,
            profile.getCurrentLevel(),
            profile.getTargetLevel(),
            engineResult,
            careerFramework,
            Instant.now());
    evidence.markAnalyzed();
    evidenceRepository.save(evidence);
    return savedResponse;
  }

  private void validateEngineResult(
      EvidenceAnalysisResponse result, CareerFramework careerFramework, CareerProfile profile) {
    if (result == null
        || result.estimatedLevel() == null
        || result.confidence() == null
        || result.reasoning() == null
        || result.reasoning().isBlank()
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
}
