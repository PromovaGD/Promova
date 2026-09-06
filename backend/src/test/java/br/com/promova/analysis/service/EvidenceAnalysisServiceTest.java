package br.com.promova.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
import br.com.promova.framework.CareerFramework;
import br.com.promova.framework.CareerLevel;
import br.com.promova.framework.FrameworkProvider;
import br.com.promova.profile.CareerProfile;
import br.com.promova.profile.CareerProfileRepository;
import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class EvidenceAnalysisServiceTest {
  @Mock private FrameworkProvider frameworkProvider;
  @Mock private AnalysisEngine analysisEngine;
  @Mock private EvidenceRepository evidenceRepository;
  @Mock private CareerProfileRepository profileRepository;
  @Mock private SavedAnalysisRepository savedAnalysisRepository;
  @Mock private SavedAnalysisService savedAnalysisService;

  private final CareerFramework framework = framework();
  private User owner;
  private User other;
  private Evidence pendingEvidence;
  private CareerProfile profile;
  private EvidenceAnalysisService service;

  @BeforeEach
  void setUp() {
    owner = user(7L, "owner@example.com");
    other = user(8L, "other@example.com");
    pendingEvidence = evidence(owner, 41L);
    profile = new CareerProfile(owner, "L3", "L4");
    service =
        new EvidenceAnalysisService(
            frameworkProvider,
            analysisEngine,
            evidenceRepository,
            profileRepository,
            savedAnalysisRepository,
            savedAnalysisService);

    lenient().when(frameworkProvider.load()).thenReturn(framework);
  }

  @Test
  void loadsOnlyOwnedEvidenceAndPersistedProfileBeforeInvokingTheEngine() {
    EvidenceAnalysisResponse engineResult = engineResult();
    when(evidenceRepository.findByIdAndUserIdForUpdate(41L, 7L))
        .thenReturn(Optional.of(pendingEvidence));
    when(savedAnalysisRepository.findByEvidenceIdAndUserId(41L, 7L)).thenReturn(Optional.empty());
    when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(profile));
    when(analysisEngine.analyze(any(), same(framework))).thenReturn(engineResult);
    when(savedAnalysisService.saveEngineResult(
            same(pendingEvidence),
            eq("L3"),
            eq("L4"),
            eq(null),
            same(engineResult),
            same(framework),
            any(Instant.class)))
        .thenReturn(null);

    service.analyzeOwnedEvidence(owner, 41L);

    ArgumentCaptor<EvidenceAnalysisRequest> requestCaptor =
        ArgumentCaptor.forClass(EvidenceAnalysisRequest.class);
    verify(analysisEngine).analyze(requestCaptor.capture(), same(framework));
    assertThat(requestCaptor.getValue())
        .isEqualTo(new EvidenceAnalysisRequest("Refactored checkout", "L3", "L4"));
    verify(evidenceRepository).findByIdAndUserIdForUpdate(41L, 7L);
    verify(profileRepository).findByUserId(7L);
    assertThat(pendingEvidence.getStatus()).isEqualTo(EvidenceStatus.ANALYZED);
    verify(evidenceRepository).save(pendingEvidence);
  }

  @Test
  void returnsTheExistingAnalysisOnRetryWithoutInvokingTheEngineAgain() {
    SavedAnalysis existing = existingAnalysis();
    when(evidenceRepository.findByIdAndUserIdForUpdate(41L, 7L))
        .thenReturn(Optional.of(pendingEvidence));
    when(savedAnalysisRepository.findByEvidenceIdAndUserId(41L, 7L))
        .thenReturn(Optional.of(existing));
    when(savedAnalysisService.toResponseForTransaction(existing)).thenReturn(null);

    service.analyzeOwnedEvidence(owner, 41L);

    verify(analysisEngine, never()).analyze(any(), any());
    verify(profileRepository, never()).findByUserId(any());
    verify(evidenceRepository, never()).save(any());
  }

  @Test
  void rejectsAnotherUsersEvidenceBeforeLoadingTheirProfileOrCallingTheEngine() {
    when(evidenceRepository.findByIdAndUserIdForUpdate(41L, 8L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.analyzeOwnedEvidence(other, 41L))
        .isInstanceOf(ResponseStatusException.class)
        .extracting("statusCode")
        .isEqualTo(HttpStatus.NOT_FOUND);

    verify(savedAnalysisRepository, never()).findByEvidenceIdAndUserId(any(), any());
    verify(profileRepository, never()).findByUserId(any());
    verify(analysisEngine, never()).analyze(any(), any());
  }

  @Test
  void leavesPendingEvidenceAndDoesNotSaveWhenTheEngineFails() {
    when(evidenceRepository.findByIdAndUserIdForUpdate(41L, 7L))
        .thenReturn(Optional.of(pendingEvidence));
    when(savedAnalysisRepository.findByEvidenceIdAndUserId(41L, 7L)).thenReturn(Optional.empty());
    when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(profile));
    when(analysisEngine.analyze(any(), same(framework)))
        .thenThrow(new IllegalStateException("engine unavailable"));

    assertThatThrownBy(() -> service.analyzeOwnedEvidence(owner, 41L))
        .isInstanceOf(IllegalStateException.class);

    assertThat(pendingEvidence.getStatus()).isEqualTo(EvidenceStatus.PENDING);
    verify(savedAnalysisService, never())
        .saveEngineResult(any(), any(), any(), any(), any(), any(), any());
    verify(evidenceRepository, never()).save(any());
  }

  @Test
  void rejectsInvalidPersistedProfileBeforeCallingTheEngine() {
    CareerProfile invalid = new CareerProfile(owner, "L4", "L3");
    when(evidenceRepository.findByIdAndUserIdForUpdate(41L, 7L))
        .thenReturn(Optional.of(pendingEvidence));
    when(savedAnalysisRepository.findByEvidenceIdAndUserId(41L, 7L)).thenReturn(Optional.empty());
    when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(invalid));

    assertThatThrownBy(() -> service.analyzeOwnedEvidence(owner, 41L))
        .isInstanceOf(ResponseStatusException.class)
        .extracting("statusCode")
        .isEqualTo(HttpStatus.BAD_REQUEST);

    verify(analysisEngine, never()).analyze(any(), any());
    verify(savedAnalysisService, never())
        .saveEngineResult(any(), any(), any(), any(), any(), any(), any());
    assertThat(pendingEvidence.getStatus()).isEqualTo(EvidenceStatus.PENDING);
  }

  private EvidenceAnalysisResponse engineResult() {
    return new EvidenceAnalysisResponse(
        "L4", Confidence.HIGH, "Server-owned result", List.of("Ownership"), List.of("Add metrics"));
  }

  private CareerFramework framework() {
    LinkedHashMap<String, CareerLevel> levels = new LinkedHashMap<>();
    levels.put("L3", new CareerLevel("Engineer"));
    levels.put("L4", new CareerLevel("Senior Engineer"));
    levels.put("L5", new CareerLevel("Staff Engineer"));
    return new CareerFramework(levels);
  }

  private SavedAnalysis existingAnalysis() {
    SavedAnalysis saved =
        new SavedAnalysis(
            "github:acme/project#7",
            owner,
            "GitHub",
            "PR #7",
            "Refactored checkout",
            "L3",
            "L4",
            "L4",
            "high",
            "Server-owned result",
            "[\"Ownership\"]",
            "[\"Add metrics\"]",
            "Aligned",
            Instant.parse("2026-05-12T10:00:00Z"));
    ReflectionTestUtils.setField(saved, "id", 99L);
    return saved;
  }

  private User user(Long id, String email) {
    User user = new User("Employee", email, "hash", UserRole.EMPLOYEE);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  private Evidence evidence(User user, Long id) {
    Evidence evidence =
        new Evidence(
            user,
            "GitHub",
            "github:acme/project#7",
            "PR #7",
            "Refactored checkout",
            "https://github.test/pr/7",
            Instant.parse("2026-05-12T10:00:00Z"));
    ReflectionTestUtils.setField(evidence, "id", id);
    return evidence;
  }
}
