package br.com.promova.analysis.review.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Browser input for one review event. Reviewer identity and timestamps are intentionally absent;
 * both are derived from the authenticated server state. Comments are optional and capped at 2,000
 * characters.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SavedAnalysisReviewRequest(
    @NotBlank String status,
    @Size(max = MAX_COMMENT_LENGTH) String comment) {
  public static final int MAX_COMMENT_LENGTH = 2_000;
}
