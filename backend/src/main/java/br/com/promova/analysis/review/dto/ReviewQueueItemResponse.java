package br.com.promova.analysis.review.dto;

import br.com.promova.analysis.review.ReviewStatus;
import java.time.Instant;

public record ReviewQueueItemResponse(
    Long analysisId,
    Long ownerId,
    String employeeName,
    String employeeEmail,
    String source,
    String sourceMeta,
    String impactLevel,
    String confidence,
    Instant createdAt,
    ReviewStatus currentStatus,
    Long latestReviewId) {}
