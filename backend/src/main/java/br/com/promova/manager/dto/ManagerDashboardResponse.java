package br.com.promova.manager.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ManagerDashboardResponse(
    LocalDate from,
    LocalDate to,
    boolean demoData,
    long peopleCount,
    long evidenceCount,
    long analysisCount,
    long unreviewedCount,
    long needsContextCount,
    long acceptedCount,
    long overdueObjectiveCount,
    long dueSoonObjectiveCount,
    long peopleWithoutActiveObjective,
    long unreviewedOlderThanThreshold,
    int attentionDays,
    long peopleWithoutRecentCapture,
    Instant lastCaptureAt,
    long criteriaWithEvidence,
    long criteriaCount,
    long failedAnalysisRuns,
    Double medianFirstReviewHours,
    long firstReviewSample,
    List<TrendPoint> trend,
    List<StatusBucket> statuses,
    List<SourceBucket> sources,
    List<AttentionItem> attention,
    List<HighlightItem> highlights,
    PullRequestMetrics pullRequests) {
  public record TrendPoint(LocalDate start, LocalDate end, int evidence, int analyses, int reviews) {}
  public record StatusBucket(String status, long count) {}
  public record SourceBucket(String source, long count) {}
  public record AttentionItem(
      String kind, Long employeeId, String employeeName, Long resourceId, String title, Instant date) {}
  public record HighlightItem(
      Long analysisId, Long employeeId, String employeeName, String title,
      String source, String impactLevel, String reviewStatus, Instant createdAt) {}
  public record PullRequestMetrics(
      long registered, long merged, long open, long closedWithoutMerge,
      long withHumanReview, long commitsOnKnownPulls, long commitCountSample,
      Double medianFirstRequestHours, long firstRequestSample,
      Double medianFirstReviewHours, long firstReviewSample) {}
}
