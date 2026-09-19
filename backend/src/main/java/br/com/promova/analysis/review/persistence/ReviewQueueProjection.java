package br.com.promova.analysis.review.persistence;

import java.time.OffsetDateTime;

public interface ReviewQueueProjection {
  Long getAnalysisId();
  Long getOwnerId();
  String getEmployeeName();
  String getEmployeeEmail();
  String getSource();
  String getSourceMeta();
  String getImpactLevel();
  String getConfidence();
  OffsetDateTime getCreatedAt();
  String getCurrentStatus();
  Long getLatestReviewId();
}
