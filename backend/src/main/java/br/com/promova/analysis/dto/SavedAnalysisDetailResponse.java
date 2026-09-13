package br.com.promova.analysis.dto;

import br.com.promova.analysis.review.ReviewStatus;
import java.time.Instant;
import java.util.List;

/** Immutable saved snapshot plus canonical owner/source links and current review projection. */
public record SavedAnalysisDetailResponse(
    String id,
    Long analysisId,
    Long ownerId,
    Long evidenceId,
    String source,
    String sourceMeta,
    String evidence,
    String currentLevel,
    String targetLevel,
    String userObservation,
    String impactLevel,
    String confidence,
    String justification,
    List<String> competencies,
    List<String> suggestions,
    String readiness,
    Instant createdAt,
    ReviewStatus currentReviewStatus,
    Long latestReviewId) {
  public SavedAnalysisDetailResponse {
    competencies = competencies == null ? List.of() : List.copyOf(competencies);
    suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    currentReviewStatus =
        currentReviewStatus == null ? ReviewStatus.UNREVIEWED : currentReviewStatus;
  }
}
