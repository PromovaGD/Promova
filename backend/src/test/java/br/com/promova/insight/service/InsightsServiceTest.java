package br.com.promova.insight.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.promova.analysis.persistence.SavedAnalysis;
import br.com.promova.analysis.persistence.SavedAnalysisRepository;
import br.com.promova.evidence.Evidence;
import br.com.promova.framework.CareerFramework;
import br.com.promova.framework.CareerLevel;
import br.com.promova.framework.FrameworkProvider;
import br.com.promova.insight.dto.InsightsResponse;
import br.com.promova.user.User;
import br.com.promova.user.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InsightsServiceTest {
  private static final Instant FROM = Instant.parse("2026-05-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-05-31T23:59:59.999Z");

  @Mock private SavedAnalysisRepository savedAnalysisRepository;
  @Mock private FrameworkProvider frameworkProvider;

  private User owner;
  private User other;
  private CareerFramework framework;
  private InsightsService service;

  @BeforeEach
  void setUp() {
    owner = user(7L, "owner@example.com");
    other = user(8L, "other@example.com");
    framework = framework();
    when(frameworkProvider.load()).thenReturn(framework);
    service = new InsightsService(savedAnalysisRepository, frameworkProvider, new ObjectMapper());
  }

  @Test
  void aggregatesExactCountsCoverageGapsAndTrendForAnInclusiveWindow() {
    SavedAnalysis testing =
        analysis(
            3L,
            "analysis-testing",
            "GitHub",
            "PR #3",
            "L10",
            List.of("Testing"),
            Instant.parse("2026-05-12T10:00:00Z"));
    SavedAnalysis security =
        analysis(
            2L,
            "analysis-security",
            "Jira",
            "PROM-2",
            "L2",
            List.of("Security"),
            Instant.parse("2026-05-10T09:00:00Z"));
    when(savedAnalysisRepository.findByUserAndDateRange(owner, FROM, TO))
        .thenReturn(List.of(testing, security));

    InsightsResponse response = service.summarizeForUser(owner, FROM, TO);

    assertThat(response.totalEvidence()).isEqualTo(2);
    assertThat(response.criteriaCount()).isEqualTo(4);
    assertThat(response.criteriaWithEvidence()).isEqualTo(2);
    assertThat(response.sourceDistribution())
        .extracting(InsightsResponse.Distribution::label)
        .containsExactly("GitHub", "Jira");
    assertThat(response.sourceDistribution())
        .extracting(InsightsResponse.Distribution::count)
        .containsExactly(1, 1);
    assertThat(response.sourceDistribution())
        .extracting(InsightsResponse.Distribution::percentage)
        .containsExactly(50, 50);
    assertThat(response.estimatedLevelDistribution())
        .extracting(InsightsResponse.Distribution::label)
        .containsExactly("L2", "L10");
    assertThat(response.estimatedLevelDistribution())
        .extracting(InsightsResponse.Distribution::count)
        .containsExactly(1, 1);

    assertThat(response.criterionCoverage())
        .extracting(
            coverage ->
                coverage.level()
                    + ":"
                    + coverage.criterion()
                    + ":"
                    + coverage.status())
        .containsExactly(
            "L2:Security:SUPPORTED",
            "L2:Delivery:NO_EVIDENCE",
            "L10:Testing:SUPPORTED",
            "L10:Architecture:NO_EVIDENCE");
    assertThat(response.criterionCoverage().get(0).supportingEvidence())
        .extracting(InsightsResponse.EvidenceReference::id)
        .containsExactly("analysis-security");
    assertThat(response.criterionCoverage().get(0).supportingEvidence())
        .extracting(InsightsResponse.EvidenceReference::evidenceId)
        .containsExactly((Long) null);
    assertThat(response.criterionCoverage().get(2).supportingEvidence())
        .extracting(InsightsResponse.EvidenceReference::id)
        .containsExactly("analysis-testing");
    assertThat(response.criterionCoverage().get(2).supportingEvidence())
        .extracting(InsightsResponse.EvidenceReference::evidenceId)
        .containsExactly((Long) null);
    assertThat(response.gaps())
        .extracting(gap -> gap.level() + ":" + gap.criterion() + ":" + gap.status())
        .containsExactly("L2:Delivery:NO_EVIDENCE", "L10:Architecture:NO_EVIDENCE");

    assertThat(response.recentTrend())
        .extracting(InsightsResponse.TrendBucket::count)
        .containsExactly(0, 2, 0, 0, 0, 0);
    assertThat(response.recentTrend())
        .extracting(InsightsResponse.TrendBucket::percentage)
        .containsExactly(0, 100, 0, 0, 0, 0);
    assertThat(response.recentTrend().get(1).label()).isEqualTo("07/05 - 12/05");
    verify(savedAnalysisRepository).findByUserAndDateRange(owner, FROM, TO);
  }

  @Test
  void returnsEmptyDistributionsAndExplicitNoEvidenceForAnEmptyWindow() {
    when(savedAnalysisRepository.findByUserAndDateRange(owner, null, null)).thenReturn(List.of());

    InsightsResponse response = service.summarizeForUser(owner, null, null);

    assertThat(response.totalEvidence()).isZero();
    assertThat(response.sourceDistribution()).isEmpty();
    assertThat(response.estimatedLevelDistribution()).isEmpty();
    assertThat(response.recentTrend()).isEmpty();
    assertThat(response.criteriaCount()).isEqualTo(4);
    assertThat(response.criteriaWithEvidence()).isZero();
    assertThat(response.criterionCoverage())
        .allMatch(coverage -> coverage.status() == InsightsResponse.CoverageStatus.NO_EVIDENCE);
    assertThat(response.gaps()).hasSize(4);
  }

  @Test
  void rendersAOneItemWindowWithoutNullTrendValues() {
    SavedAnalysis one =
        analysis(
            4L,
            "analysis-one",
            "GitHub",
            "PR #4",
            "L2",
            List.of("Testing"),
            Instant.parse("2026-05-12T10:00:00Z"));
    when(savedAnalysisRepository.findByUserAndDateRange(owner, null, null)).thenReturn(List.of(one));

    InsightsResponse response = service.summarizeForUser(owner, null, null);

    assertThat(response.totalEvidence()).isEqualTo(1);
    assertThat(response.recentTrend()).hasSize(1);
    assertThat(response.recentTrend().get(0).count()).isEqualTo(1);
    assertThat(response.recentTrend().get(0).percentage()).isEqualTo(100);
    assertThat(response.recentTrend().get(0).bucketStart()).isEqualTo(one.getCreatedAt());
    assertThat(response.recentTrend().get(0).bucketEnd()).isEqualTo(one.getCreatedAt());
  }

  @Test
  void scopesTheRepositoryReadToTheAuthenticatedUser() {
    SavedAnalysis owned =
        analysis(
            5L,
            "owner-analysis",
            "GitHub",
            "PR #5",
            "L2",
            List.of("Testing"),
            Instant.parse("2026-05-12T10:00:00Z"));
    when(savedAnalysisRepository.findByUserAndDateRange(owner, null, null)).thenReturn(List.of(owned));
    when(savedAnalysisRepository.findByUserAndDateRange(other, null, null)).thenReturn(List.of());

    InsightsResponse ownerResponse = service.summarizeForUser(owner, null, null);
    InsightsResponse otherResponse = service.summarizeForUser(other, null, null);

    assertThat(ownerResponse.totalEvidence()).isEqualTo(1);
    assertThat(otherResponse.totalEvidence()).isZero();
    verify(savedAnalysisRepository).findByUserAndDateRange(owner, null, null);
    verify(savedAnalysisRepository).findByUserAndDateRange(other, null, null);
  }

  @Test
  void handlesAReorderedAndPartiallyPopulatedFramework() {
    LinkedHashMap<String, CareerLevel> levels = new LinkedHashMap<>();
    levels.put("L10", new CareerLevel("Senior", "Senior", Map.of("Security", "Protects systems")));
    levels.put("L2", new CareerLevel("Junior", "Junior", Map.of()));
    CareerFramework partialFramework = new CareerFramework(levels);
    when(frameworkProvider.load()).thenReturn(partialFramework);
    SavedAnalysis one =
        analysis(
            6L,
            "partial-analysis",
            "Jira",
            "PROM-6",
            "L10",
            List.of("Security"),
            Instant.parse("2026-05-12T10:00:00Z"));
    when(savedAnalysisRepository.findByUserAndDateRange(owner, null, null)).thenReturn(List.of(one));

    InsightsResponse response = service.summarizeForUser(owner, null, null);

    assertThat(response.criteriaCount()).isEqualTo(1);
    assertThat(response.criteriaWithEvidence()).isEqualTo(1);
    assertThat(response.criterionCoverage()).hasSize(1);
    assertThat(response.criterionCoverage().get(0).level()).isEqualTo("L10");
    assertThat(response.criterionCoverage().get(0).status())
        .isEqualTo(InsightsResponse.CoverageStatus.SUPPORTED);
    assertThat(response.gaps()).isEmpty();
  }

  @Test
  void exposesTheOwnerScopedNumericEvidenceIdForTrustedAnalysisReferences() {
    Evidence evidence =
        new Evidence(
            owner,
            "GitHub",
            "github:trusted#41",
            "PR #41",
            "Trusted evidence",
            "https://github.example/41",
            Instant.parse("2026-05-12T10:00:00Z"));
    ReflectionTestUtils.setField(evidence, "id", 41L);
    SavedAnalysis trusted =
        new SavedAnalysis(
            evidence,
            "L2",
            "L10",
            "L2",
            "high",
            "Justification",
            writeJson(List.of("Security")),
            "[]",
            "Readiness",
            Instant.parse("2026-05-12T10:00:00Z"));
    ReflectionTestUtils.setField(trusted, "id", 11L);
    when(savedAnalysisRepository.findByUserAndDateRange(owner, null, null))
        .thenReturn(List.of(trusted));

    InsightsResponse response = service.summarizeForUser(owner, null, null);

    InsightsResponse.EvidenceReference reference =
        response.criterionCoverage().get(0).supportingEvidence().get(0);
    assertThat(reference.id()).isEqualTo("github:trusted#41");
    assertThat(reference.evidenceId()).isEqualTo(41L);
  }

  @Test
  void ignoresARepositoryRowOwnedByAnotherUser() {
    Evidence otherEvidence =
        new Evidence(
            other,
            "GitHub",
            "github:other#99",
            "PR #99",
            "Other user's evidence",
            null,
            Instant.parse("2026-05-12T10:00:00Z"));
    ReflectionTestUtils.setField(otherEvidence, "id", 99L);
    SavedAnalysis otherAnalysis =
        new SavedAnalysis(
            otherEvidence,
            "L2",
            "L10",
            "L2",
            "high",
            "Justification",
            writeJson(List.of("Security")),
            "[]",
            "Readiness",
            Instant.parse("2026-05-12T10:00:00Z"));
    when(savedAnalysisRepository.findByUserAndDateRange(owner, null, null))
        .thenReturn(List.of(otherAnalysis));

    InsightsResponse response = service.summarizeForUser(owner, null, null);

    assertThat(response.totalEvidence()).isZero();
    assertThat(response.criterionCoverage().get(0).supportingEvidence()).isEmpty();
  }

  @Test
  void doesNotCarryACompetencyAcrossDifferentFrameworkLevels() {
    LinkedHashMap<String, CareerLevel> levels = new LinkedHashMap<>();
    levels.put("L2", new CareerLevel("Junior", "Junior", Map.of("Testing", "Basic tests")));
    levels.put("L10", new CareerLevel("Senior", "Senior", Map.of("Testing", "Edge cases")));
    when(frameworkProvider.load()).thenReturn(new CareerFramework(levels));
    SavedAnalysis one =
        analysis(
            7L,
            "level-specific-analysis",
            "GitHub",
            "PR #7",
            "L10",
            List.of("Testing"),
            Instant.parse("2026-05-12T10:00:00Z"));
    when(savedAnalysisRepository.findByUserAndDateRange(owner, null, null)).thenReturn(List.of(one));

    InsightsResponse response = service.summarizeForUser(owner, null, null);

    assertThat(response.criterionCoverage())
        .extracting(coverage -> coverage.level() + ":" + coverage.status())
        .containsExactly("L2:NO_EVIDENCE", "L10:SUPPORTED");
  }

  private CareerFramework framework() {
    LinkedHashMap<String, CareerLevel> levels = new LinkedHashMap<>();
    LinkedHashMap<String, String> juniorCriteria = new LinkedHashMap<>();
    juniorCriteria.put("Security", "Understands security");
    juniorCriteria.put("Delivery", "Ships incrementally");
    LinkedHashMap<String, String> seniorCriteria = new LinkedHashMap<>();
    seniorCriteria.put("Testing", "Tests edge cases");
    seniorCriteria.put("Architecture", "Assesses system boundaries");
    levels.put("L2", new CareerLevel("Junior", "Junior", juniorCriteria));
    levels.put("L10", new CareerLevel("Senior", "Senior", seniorCriteria));
    return new CareerFramework(levels);
  }

  private SavedAnalysis analysis(
      Long id,
      String externalId,
      String source,
      String sourceMeta,
      String impactLevel,
      List<String> competencies,
      Instant createdAt) {
    SavedAnalysis analysis =
        new SavedAnalysis(
            externalId,
            owner,
            source,
            sourceMeta,
            "Saved evidence",
            "L2",
            "L10",
            impactLevel,
            "high",
            "Justification",
            writeJson(competencies),
            "[]",
            "Readiness",
            createdAt);
    ReflectionTestUtils.setField(analysis, "id", id);
    return analysis;
  }

  private String writeJson(List<String> values) {
    try {
      return new ObjectMapper().writeValueAsString(values);
    } catch (Exception exception) {
      throw new AssertionError(exception);
    }
  }

  private User user(Long id, String email) {
    User user = new User("Employee", email, "hash", UserRole.EMPLOYEE);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }
}
