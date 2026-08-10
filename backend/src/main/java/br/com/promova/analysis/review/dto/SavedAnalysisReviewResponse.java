package br.com.promova.analysis.review.dto;

import br.com.promova.analysis.review.ReviewStatus;
import java.time.Instant;

/** One immutable review event in deterministic server order. */
public record SavedAnalysisReviewResponse(
    Long id,
    Long reviewerId,
    String reviewerName,
    String reviewerEmail,
    Instant createdAt,
    ReviewStatus status,
    String comment) {}
