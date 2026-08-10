package br.com.promova.analysis.review.dto;

import br.com.promova.analysis.review.ReviewStatus;
import java.util.List;

/** Current review state and the complete append-only history for one saved analysis. */
public record AnalysisReviewResponse(
    Long analysisId, ReviewStatus currentStatus, List<SavedAnalysisReviewResponse> history) {
  public AnalysisReviewResponse {
    currentStatus = currentStatus == null ? ReviewStatus.UNREVIEWED : currentStatus;
    history = history == null ? List.of() : List.copyOf(history);
  }
}
