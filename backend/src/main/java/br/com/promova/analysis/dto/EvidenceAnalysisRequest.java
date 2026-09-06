package br.com.promova.analysis.dto;

import jakarta.validation.constraints.NotBlank;

public record EvidenceAnalysisRequest(
    @NotBlank(message = "evidence is required") String evidence,
    String userObservation,
    @NotBlank(message = "currentLevel is required") String currentLevel,
    @NotBlank(message = "targetLevel is required") String targetLevel) {
  public EvidenceAnalysisRequest(String evidence, String currentLevel, String targetLevel) {
    this(evidence, null, currentLevel, targetLevel);
  }
}
