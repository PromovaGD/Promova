package br.com.promova.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.promova.analysis.dto.EvidenceAnalysisRequest;
import br.com.promova.analysis.dto.EvidenceAnalysisResponse;
import br.com.promova.analysis.engine.AnalysisEngine;
import br.com.promova.analysis.engine.Confidence;
import br.com.promova.analysis.persistence.SavedAnalysis;
import br.com.promova.analysis.persistence.SavedAnalysisRepository;
import br.com.promova.evidence.Evidence;
import br.com.promova.evidence.EvidenceRepository;
import br.com.promova.evidence.EvidenceStatus;
import br.com.promova.profile.CareerProfile;
import br.com.promova.profile.CareerProfileRepository;
import br.com.promova.user.User;
import br.com.promova.user.UserRepository;
import br.com.promova.user.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
class TrustedAnalysisPersistenceIntegrationTest {
  private static final AtomicInteger USERS = new AtomicInteger();

  @Autowired private EvidenceAnalysisService analysisService;
  @Autowired private UserRepository userRepository;
  @Autowired private CareerProfileRepository profileRepository;
  @Autowired private EvidenceRepository evidenceRepository;
  @Autowired private SavedAnalysisRepository savedAnalysisRepository;

  @MockitoBean private AnalysisEngine analysisEngine;

  private User owner;

  @BeforeEach
  void setUp() {
    int suffix = USERS.incrementAndGet();
    owner =
        userRepository.save(
            new User(
                "Trusted Owner " + suffix,
                "trusted-owner-" + suffix + "@example.com",
                "hash",
                UserRole.EMPLOYEE));
    profileRepository.save(new CareerProfile(owner, "L3", "L4"));
    reset(analysisEngine);
  }

  @Test
  void persistsEngineOutputAndImmutableEvidenceSnapshotAndIsIdempotent() {
    Evidence evidence = saveEvidence("Refactored checkout and increased coverage");
    EvidenceAnalysisResponse engineResult =
        new EvidenceAnalysisResponse(
            "L4", Confidence.HIGH, "Measured system improvement", List.of("Ownership"), List.of("Add metrics"));
    when(analysisEngine.analyze(any(EvidenceAnalysisRequest.class), any())).thenReturn(engineResult);

    var first = analysisService.analyzeOwnedEvidence(owner, evidence.getId());
    var second = analysisService.analyzeOwnedEvidence(owner, evidence.getId());

    assertThat(first.id()).isEqualTo(second.id());
    assertThat(first.impactLevel()).isEqualTo("L4");
    assertThat(first.confidence()).isEqualTo("high");
    assertThat(first.justification()).isEqualTo("Measured system improvement");
    assertThat(first.competencies()).containsExactly("Ownership");
    assertThat(first.suggestions()).containsExactly("Add metrics");
    assertThat(first.readiness()).contains("L4");

    SavedAnalysis saved =
        savedAnalysisRepository
            .findByEvidenceIdAndUserId(evidence.getId(), owner.getId())
            .orElseThrow();
    assertThat(saved.getExternalId()).isEqualTo("github:promova/app#7");
    assertThat(saved.getSource()).isEqualTo("GitHub");
    assertThat(saved.getSourceMeta()).isEqualTo("PR #7 - promova/app");
    assertThat(saved.getEvidence()).isEqualTo("Refactored checkout and increased coverage");
    assertThat(saved.getCurrentLevel()).isEqualTo("L3");
    assertThat(saved.getTargetLevel()).isEqualTo("L4");
    assertThat(saved.getEvidenceEntity().getId()).isEqualTo(evidence.getId());

    Evidence reloaded = evidenceRepository.findById(evidence.getId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(EvidenceStatus.ANALYZED);
    verify(analysisEngine, times(1)).analyze(any(EvidenceAnalysisRequest.class), any());
  }

  @Test
  void sendsServerLoadedEvidenceAndProfileLevelsToTheEngine() {
    Evidence evidence = saveEvidence("Helped the team fix production bugs");
    when(analysisEngine.analyze(any(EvidenceAnalysisRequest.class), any()))
        .thenReturn(
            new EvidenceAnalysisResponse(
                "L3", Confidence.MEDIUM, "Team contribution", List.of(), List.of()));

    analysisService.analyzeOwnedEvidence(owner, evidence.getId());

    org.mockito.ArgumentCaptor<EvidenceAnalysisRequest> request =
        org.mockito.ArgumentCaptor.forClass(EvidenceAnalysisRequest.class);
    verify(analysisEngine).analyze(request.capture(), any());
    assertThat(request.getValue().evidence()).isEqualTo("Helped the team fix production bugs");
    assertThat(request.getValue().currentLevel()).isEqualTo("L3");
    assertThat(request.getValue().targetLevel()).isEqualTo("L4");
  }

  @Test
  void rollsBackWithoutAnAnalysisWhenTheEngineFails() {
    Evidence evidence = saveEvidence("Evidence that should remain pending");
    when(analysisEngine.analyze(any(EvidenceAnalysisRequest.class), any()))
        .thenThrow(new IllegalStateException("engine unavailable"));

    assertThatThrownBy(() -> analysisService.analyzeOwnedEvidence(owner, evidence.getId()))
        .isInstanceOf(IllegalStateException.class);

    assertThat(
            savedAnalysisRepository.findByEvidenceIdAndUserId(evidence.getId(), owner.getId()))
        .isEmpty();
    assertThat(evidenceRepository.findById(evidence.getId()).orElseThrow().getStatus())
        .isEqualTo(EvidenceStatus.PENDING);
  }

  @Test
  void rejectsInvalidProfileFrameworkStateBeforeCallingTheEngine() {
    CareerProfile profile = profileRepository.findByUserId(owner.getId()).orElseThrow();
    profile.updateLevels("L4", "L3");
    profileRepository.save(profile);
    Evidence evidence = saveEvidence("Invalid profile must not reach the engine");

    assertThatThrownBy(() -> analysisService.analyzeOwnedEvidence(owner, evidence.getId()))
        .isInstanceOf(ResponseStatusException.class)
        .extracting("statusCode")
        .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);

    verify(analysisEngine, times(0)).analyze(any(), any());
    assertThat(evidenceRepository.findById(evidence.getId()).orElseThrow().getStatus())
        .isEqualTo(EvidenceStatus.PENDING);
  }

  @Test
  void hidesOwnedEvidenceFromAnotherAuthenticatedUser() {
    Evidence evidence = saveEvidence("Owner-only evidence");
    User other =
        userRepository.save(
            new User("Other", "other-" + USERS.incrementAndGet() + "@example.com", "hash", UserRole.EMPLOYEE));

    assertThatThrownBy(() -> analysisService.analyzeOwnedEvidence(other, evidence.getId()))
        .isInstanceOf(ResponseStatusException.class)
        .extracting("statusCode")
        .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);

    verify(analysisEngine, times(0)).analyze(any(), any());
    assertThat(savedAnalysisRepository.findByEvidenceIdAndUserId(evidence.getId(), owner.getId()))
        .isEmpty();
  }

  private Evidence saveEvidence(String text) {
    return evidenceRepository.save(
        new Evidence(
            owner,
            "GitHub",
            "github:promova/app#7",
            "PR #7 - promova/app",
            text,
            "https://github.test/promova/app/pull/7",
            Instant.parse("2026-05-12T10:00:00Z")));
  }
}
