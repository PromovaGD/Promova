package br.com.promova.insight.dto;

import java.time.Instant;
import java.util.List;

/**
 * Server-owned career evidence summary. Every count and bucket in this response is calculated
 * from the authenticated user's saved analyses and the framework loaded for the request.
 *
 * @param totalEvidence number of saved analyses inside the requested inclusive window
 * @param criteriaCount number of configured level/criterion pairs in the current framework
 * @param criteriaWithEvidence number of pairs with at least one supporting saved analysis
 * @param sourceDistribution counts and server-calculated percentages by source
 * @param estimatedLevelDistribution counts and server-calculated percentages by saved estimated level
 * @param criterionCoverage framework criteria with status and supporting evidence references
 * @param recentTrend deterministic calendar ranges containing saved-analysis counts
 * @param gaps current framework criteria with {@link CoverageStatus#NO_EVIDENCE}
 */
public record InsightsResponse(
    int totalEvidence,
    int criteriaCount,
    int criteriaWithEvidence,
    List<Distribution> sourceDistribution,
    List<Distribution> estimatedLevelDistribution,
    List<CriterionCoverage> criterionCoverage,
    List<TrendBucket> recentTrend,
    List<Gap> gaps) {

  public InsightsResponse {
    sourceDistribution = immutable(sourceDistribution);
    estimatedLevelDistribution = immutable(estimatedLevelDistribution);
    criterionCoverage = immutable(criterionCoverage);
    recentTrend = immutable(recentTrend);
    gaps = immutable(gaps);
  }

  private static <T> List<T> immutable(List<T> values) {
    return values == null ? List.of() : List.copyOf(values);
  }

  public record Distribution(String label, int count, int percentage) {}

  public record CriterionCoverage(
      String level,
      String levelTitle,
      String criterion,
      String description,
      CoverageStatus status,
      int evidenceCount,
      List<EvidenceReference> supportingEvidence) {
    public CriterionCoverage {
      supportingEvidence = supportingEvidence == null ? List.of() : List.copyOf(supportingEvidence);
    }
  }

  /**
   * A saved-analysis reference used by the coverage drill-down.
   *
   * <p>{@code id} remains the stable saved-analysis/external reference for display and for the
   * dashboard's existing analysis lookup. {@code evidenceId} is the numeric, owner-scoped
   * Evidence id when this analysis came from the trusted evidence workflow. It is null for legacy
   * saved analyses that have no linked Evidence entity and therefore must not be rendered as a
   * drill-down link.
   */
  public record EvidenceReference(
      String id, Long evidenceId, String title, String source, Instant createdAt) {}

  public record TrendBucket(
      String label, Instant bucketStart, Instant bucketEnd, int count, int percentage) {}

  public record Gap(
      String level, String levelTitle, String criterion, String description, CoverageStatus status) {}

  public enum CoverageStatus {
    SUPPORTED,
    NO_EVIDENCE
  }
}
