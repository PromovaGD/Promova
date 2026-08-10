package br.com.promova.analysis.dto;

import java.time.Instant;
import java.util.List;

public record SavedAnalysisResponse(
    String id,
    Long userId,
    String source,
    String sourceMeta,
    String evidence,
    String currentLevel,
    String targetLevel,
    String impactLevel,
    String confidence,
    String justification,
    List<String> competencies,
    List<String> suggestions,
    String readiness,
    Instant createdAt,
    Long analysisId) {

  /** Compatibility constructor for existing internal callers that only have the external id. */
  public SavedAnalysisResponse(
      String id,
      Long userId,
      String source,
      String sourceMeta,
      String evidence,
      String currentLevel,
      String targetLevel,
      String impactLevel,
      String confidence,
      String justification,
      List<String> competencies,
      List<String> suggestions,
      String readiness,
      Instant createdAt) {
    this(
        id,
        userId,
        source,
        sourceMeta,
        evidence,
        currentLevel,
        targetLevel,
        impactLevel,
        confidence,
        justification,
        competencies,
        suggestions,
        readiness,
        createdAt,
        null);
  }
}
